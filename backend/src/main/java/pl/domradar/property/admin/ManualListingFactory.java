package pl.domradar.property.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.ingestion.normalize.DictionaryLookup.CityRef;
import pl.domradar.ingestion.normalize.DictionaryLookup.DistrictRef;
import pl.domradar.ingestion.normalize.Geocoder;
import pl.domradar.ingestion.normalize.NormalizedListing;
import pl.domradar.property.admin.dto.OfferCreateRequest;
import pl.domradar.property.admin.dto.PropertyAttributesRequest;
import pl.domradar.property.dictionary.City;
import pl.domradar.property.dictionary.CityRepository;
import pl.domradar.property.dictionary.District;
import pl.domradar.property.dictionary.DistrictRepository;

import java.math.RoundingMode;
import java.util.*;

/** Buduje znormalizowane ogłoszenie z formularza administratora (walidacja słowników + geokodowanie). */
@Component
@RequiredArgsConstructor
public class ManualListingFactory {

    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;
    private final Geocoder geocoder;

    public record Location(City city, District district) {
    }

    /** Sprawdza, że miasto istnieje, a dzielnica należy do miasta (422 INVALID_REFERENCE). */
    public Location resolve(Long cityId, Long districtId) {
        City city = cityRepository.findById(cityId)
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_REFERENCE, "Nieznane miasto: " + cityId, Map.of("field", "cityId")));
        District district = null;
        if (districtId != null) {
            district = districtRepository.findById(districtId)
                    .filter(d -> d.getCity().getId().equals(city.getId()))
                    .orElseThrow(() -> new DomainException(ErrorCode.INVALID_REFERENCE,
                            "Dzielnica " + districtId + " nie należy do miasta " + city.getName(), Map.of("field", "districtId")));
        }
        return new Location(city, district);
    }

    public double[] coordinates(PropertyAttributesRequest a, Location location) {
        if (a.lat() != null && a.lon() != null) {
            return new double[]{a.lat(), a.lon()};
        }
        City c = location.city();
        District d = location.district();
        Geocoder.GeoPoint point = geocoder.geocode(new CityRef(c.getId(), c.getName(), c.getLat(), c.getLon()),
                d == null ? null : new DistrictRef(d.getId(), c.getId(), d.getName(), d.getLat(), d.getLon()), a.street());
        return new double[]{point.lat(), point.lon()};
    }

    public NormalizedListing build(OfferCreateRequest r, PropertyAttributesRequest a) {
        Location location = resolve(a.cityId(), a.districtId());
        double[] coords = coordinates(a, location);
        Set<String> features = a.features() == null ? Set.of()
                : new TreeSet<>(a.features().stream().filter(Objects::nonNull).map(f -> f.trim().toUpperCase(Locale.ROOT)).toList());
        String externalId = r.externalId() == null || r.externalId().isBlank()
                ? "MAN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT) : r.externalId().trim();
        return NormalizedListing.builder()
                .externalId(externalId)
                .url(r.url())
                .title(r.title().trim())
                .description(r.description())
                .price(r.price().setScale(2, RoundingMode.HALF_UP))
                .propertyType(a.propertyType())
                .transactionType(a.transactionType())
                .marketType(a.marketType())
                .cityId(location.city().getId())
                .cityName(location.city().getName())
                .districtId(location.district() == null ? null : location.district().getId())
                .districtName(location.district() == null ? null : location.district().getName())
                .street(a.street())
                .lat(coords[0])
                .lon(coords[1])
                .geocoded(a.lat() == null || a.lon() == null)
                .area(a.area().setScale(2, RoundingMode.HALF_UP))
                .rooms(a.rooms())
                .floor(a.floor())
                .totalFloors(a.totalFloors())
                .yearBuilt(a.yearBuilt())
                .featureCodes(features)
                .photos(r.photos() == null ? List.of() : r.photos())
                .agencyName(r.privateSeller() ? null : r.agencyName())
                .privateSeller(r.privateSeller())
                .build();
    }
}
