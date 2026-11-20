package pl.domradar.ingestion.normalize;

import org.springframework.stereotype.Component;
import pl.domradar.ingestion.IngestionProperties;
import pl.domradar.ingestion.adapter.RawListing;
import pl.domradar.ingestion.normalize.DictionaryLookup.CityRef;
import pl.domradar.ingestion.normalize.DictionaryLookup.DistrictRef;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Pipeline normalizacji: {@link RawListing} → {@link NormalizedListing}.
 * Waluta → PLN, metraż → m², pokoje → int, enumy typu/rynku/transakcji, dzielnica → słownik, geokodowanie.
 */
@Component
public class ListingNormalizer {

    private static final double MAX_DISTANCE_FROM_CITY_DEG = 0.35;
    private static final double NEAREST_DISTRICT_MAX_KM = 3.0;

    private final DictionaryLookup dictionary;
    private final Geocoder geocoder;
    private final IngestionProperties properties;

    public ListingNormalizer(DictionaryLookup dictionary, Geocoder geocoder, IngestionProperties properties) {
        this.dictionary = dictionary;
        this.geocoder = geocoder;
        this.properties = properties;
    }

    public NormalizedListing normalize(RawListing raw) {
        String externalId = require(blankToNull(raw.externalId()), "brak identyfikatora ogłoszenia");
        String title = require(blankToNull(raw.title()), "brak tytułu");

        BigDecimal price = ValueParsers.parseAmount(raw.priceText())
                .filter(p -> p.signum() > 0)
                .orElseThrow(() -> new NormalizationException("niepoprawna cena: " + raw.priceText()));
        if (ValueParsers.isEuro(raw.currency(), raw.priceText())) {
            price = price.multiply(properties.eurRate());
        }
        price = price.setScale(2, RoundingMode.HALF_UP);

        BigDecimal area = ValueParsers.parseAmount(raw.areaText())
                .filter(a -> a.compareTo(new BigDecimal("5")) >= 0 && a.compareTo(new BigDecimal("100000")) <= 0)
                .orElseThrow(() -> new NormalizationException("niepoprawny metraż: " + raw.areaText()))
                .setScale(2, RoundingMode.HALF_UP);

        String context = title + " " + (raw.description() == null ? "" : raw.description());
        PropertyType type = ValueParsers.parsePropertyType(raw.propertyTypeText())
                .or(() -> ValueParsers.parsePropertyType(title))
                .orElseThrow(() -> new NormalizationException("nieznany typ nieruchomości: " + raw.propertyTypeText()));
        BigDecimal declaredArea = area;
        area = AreaPlausibility.plausibleArea(type, area, title)
                .orElseThrow(() -> new NormalizationException("nierealny metraż: " + declaredArea + " m²"));
        TransactionType declared = ValueParsers.parseTransaction(raw.transactionText())
                .or(() -> ValueParsers.parseTransaction(context))
                .or(() -> ValueParsers.parseTransaction(raw.priceText()))
                .orElse(TransactionType.SALE);
        // ogłoszenia w złej kategorii portalu (np. "sprzedaż" za 2500 zł) — korekta po cenie za m²
        TransactionType transaction = TransactionClassifier.classify(declared, type, price, area);
        Optional<String> implausible = PricePlausibility.rejectionReason(type, transaction, price, area);
        if (implausible.isPresent()) {
            throw new NormalizationException(implausible.get());
        }

        CityRef city = dictionary.city(raw.city())
                .orElseThrow(() -> new NormalizationException("miasto spoza słownika: " + raw.city()));
        DistrictRef district = dictionary.district(city.id(), raw.district())
                .or(() -> dictionary.district(city.id(), raw.street()))
                .orElse(null);

        double lat;
        double lon;
        boolean geocoded = false;
        if (raw.lat() != null && raw.lon() != null && Math.abs(raw.lat() - city.lat()) < MAX_DISTANCE_FROM_CITY_DEG
                && Math.abs(raw.lon() - city.lon()) < MAX_DISTANCE_FROM_CITY_DEG * 1.6) {
            lat = raw.lat();
            lon = raw.lon();
            if (district == null) {
                // portal nie podał dzielnicy, ale znamy dokładne współrzędne — najbliższa dzielnica ze słownika
                district = dictionary.nearestDistrict(city.id(), lat, lon, NEAREST_DISTRICT_MAX_KM).orElse(null);
            }
        } else {
            Geocoder.GeoPoint point = geocoder.geocode(city, district, raw.street());
            lat = point.lat();
            lon = point.lon();
            geocoded = true;
        }

        ValueParsers.FloorInfo floor = ValueParsers.parseFloor(raw.floorText(), raw.totalFloorsText());
        Integer rooms = ValueParsers.parseRooms(raw.roomsText()).orElse(type == PropertyType.ROOM ? 1 : null);
        Integer year = ValueParsers.parseYear(raw.yearBuiltText()).filter(y -> y >= 1800 && y <= 2100).orElse(null);
        String agency = blankToNull(raw.agencyName());
        boolean privateSeller = ValueParsers.parsePrivateSeller(raw.sellerText()).orElse(agency == null);

        List<String> photos = raw.photos() == null ? List.of()
                : raw.photos().stream().filter(p -> p != null && p.startsWith("http")).limit(15).toList();

        return NormalizedListing.builder()
                .externalId(truncate(externalId, 100))
                .url(truncate(blankToNull(raw.url()), 1000))
                .title(truncate(ContactRedactor.redact(title.trim()), 300))
                .description(truncate(ContactRedactor.redact(blankToNull(raw.description())), 10_000))
                .price(price)
                .propertyType(type)
                .transactionType(transaction)
                .marketType(ValueParsers.parseMarket(raw.marketText()).orElse(null))
                .cityId(city.id())
                .cityName(city.name())
                .districtId(district == null ? null : district.id())
                .districtName(district == null ? null : district.name())
                .street(truncate(cleanStreet(raw.street()), 200))
                .lat(lat)
                .lon(lon)
                .geocoded(geocoded)
                .area(area)
                .rooms(rooms)
                .floor(floor.floor())
                .totalFloors(floor.totalFloors())
                .yearBuilt(year)
                .featureCodes(dictionary.features(raw.features()))
                .photos(photos)
                .agencyName(privateSeller ? null : truncate(agency, 200))
                .privateSeller(privateSeller)
                .build();
    }

    static String cleanStreet(String street) {
        String s = blankToNull(street);
        if (s == null) {
            return null;
        }
        s = s.replaceAll("(?i)^(ul\\.?|ulica)\\s+", "").trim();
        return s.isEmpty() ? null : s;
    }

    private static String require(String value, String message) {
        return Optional.ofNullable(value).orElseThrow(() -> new NormalizationException(message));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
