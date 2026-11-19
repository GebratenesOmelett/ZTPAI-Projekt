package pl.domradar.ingestion.adapter.demo;

import org.springframework.stereotype.Component;
import pl.domradar.ingestion.adapter.ImportContext;
import pl.domradar.ingestion.adapter.RawListing;
import pl.domradar.ingestion.adapter.SourceAdapter;
import pl.domradar.ingestion.adapter.demo.DemoUniverse.BaseProperty;
import pl.domradar.property.MarketType;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Stream;

/**
 * Adapter generujący syntetyczne ogłoszenia "w stylu" różnych portali. Każdy portal zawiera
 * część nieruchomości z uniwersum, z lekko innym tytułem, ceną, metrażem i formatem pól —
 * dzięki temu można pokazać normalizację, deduplikację, historię cen i archiwizację.
 */
@Component
public class DemoAdapter implements SourceAdapter {

    /** Profil portalu: prawdopodobieństwo, że nieruchomość jest na nim wystawiona, i styl formatowania pól. */
    record PortalProfile(double inclusion, int style, double priceJitter) {
    }

    private static final Map<String, PortalProfile> PROFILES = Map.of(
            "otodom", new PortalProfile(0.75, 0, 0.015),
            "morizon", new PortalProfile(0.45, 1, 0.02),
            "gratka", new PortalProfile(0.35, 2, 0.02),
            "olx", new PortalProfile(0.40, 3, 0.025));

    private static final Map<String, String> FEATURE_LABELS = Map.ofEntries(
            Map.entry("BALCONY", "balkon"), Map.entry("TERRACE", "taras"), Map.entry("GARDEN", "ogródek"),
            Map.entry("GARAGE", "garaż/miejsce parkingowe"), Map.entry("ELEVATOR", "winda"),
            Map.entry("BASEMENT", "piwnica"), Map.entry("STORAGE", "komórka lokatorska"),
            Map.entry("AIR_CONDITIONING", "klimatyzacja"), Map.entry("FURNISHED", "umeblowanie"),
            Map.entry("MONITORING", "monitoring / ochrona"), Map.entry("LOGGIA", "loggia"),
            Map.entry("SEPARATE_KITCHEN", "oddzielna kuchnia"));

    private static final String[] ADJECTIVES = {"Przestronne", "Słoneczne", "Jasne", "Ciche"};

    private final DemoUniverse universe;

    public DemoAdapter(DemoUniverse universe) {
        this.universe = universe;
    }

    @Override
    public String code() {
        return "demo";
    }

    @Override
    public String description() {
        return "Dane demonstracyjne generowane deterministycznie (duplikaty między portalami, zmiany cen, znikające oferty)";
    }

    @Override
    public Stream<RawListing> fetch(ImportContext ctx) {
        String portal = ctx.sourceCode();
        PortalProfile profile = PROFILES.getOrDefault(portal,
                new PortalProfile(0.3, Math.floorMod(portal.hashCode(), 4), 0.02));
        int run = Math.max(1, ctx.runNumber());
        String baseUrl = ctx.baseUrl() == null ? "https://example.com" : ctx.baseUrl().replaceAll("/+$", "");
        return universe.properties().stream()
                .filter(p -> p.visibleAt(run))
                .filter(p -> includedOn(p, portal, profile))
                .map(p -> toRaw(p, portal, profile, run, baseUrl));
    }

    private boolean includedOn(BaseProperty p, String portal, PortalProfile profile) {
        if (DemoUniverse.unit(universe.seed(), p.index(), portal.hashCode(), 11) < profile.inclusion()) {
            return true;
        }
        // każda nieruchomość jest na co najmniej jednym ze znanych portali — domyślnie na Otodom
        if ("otodom".equals(portal)) {
            return PROFILES.entrySet().stream().noneMatch(e ->
                    DemoUniverse.unit(universe.seed(), p.index(), e.getKey().hashCode(), 11) < e.getValue().inclusion());
        }
        return false;
    }

    RawListing toRaw(BaseProperty p, String portal, PortalProfile profile, int run, String baseUrl) {
        int style = profile.style();
        long salt = DemoUniverse.mix(universe.seed(), p.index(), portal.hashCode());
        SplittableRandom r = new SplittableRandom(salt);

        double priceFactor = 1 + (r.nextDouble() - 0.5) * 2 * profile.priceJitter();
        double price = universe.priceAt(p, run) * priceFactor;
        price = p.transaction() == TransactionType.SALE ? Math.round(price / 1000) * 1000 : Math.round(price / 50) * 50;

        String externalId = externalId(style, portal, salt);
        boolean hasCoords = style != 3 || r.nextDouble() < 0.7;
        double jitterLat = (r.nextDouble() - 0.5) * 0.0008;
        double jitterLon = (r.nextDouble() - 0.5) * 0.0012;
        String districtText = districtText(p, style, r);

        RawListing.RawListingBuilder b = RawListing.builder()
                .externalId(externalId)
                .url(baseUrl + "/demo/oferta/" + externalId)
                .title(title(p, style, r))
                .description(description(p))
                .areaText(areaText(p.area(), style))
                .roomsText(roomsText(p.rooms(), style))
                .yearBuiltText(yearText(p.yearBuilt(), style, r))
                .propertyTypeText(typeText(p.type(), style))
                .transactionText(transactionText(p.transaction(), style))
                .marketText(marketText(p.market(), style, r))
                .city(style == 3 ? p.district().cityName() + ", " + voivodeship(p.district().citySlug()) : p.district().cityName())
                .district(districtText)
                .street(streetText(p, style, r))
                .lat(hasCoords ? p.lat() + jitterLat : null)
                .lon(hasCoords ? p.lon() + jitterLon : null)
                .features(featureLabels(p.features(), style))
                .photos(photos(p))
                .agencyName(p.privateSeller() ? null : DemoPriceTable.AGENCIES.get(p.index() % DemoPriceTable.AGENCIES.size()))
                .sellerText(sellerText(p.privateSeller(), style));
        applyPrice(b, price, p, style, r);
        applyFloor(b, p, style, r);
        return b.build();
    }

    private static void applyPrice(RawListing.RawListingBuilder b, double price, BaseProperty p, int style, SplittableRandom r) {
        boolean rent = p.transaction() == TransactionType.RENT;
        if (style == 0 && !rent && price > 2_500_000 && r.nextDouble() < 0.3) {
            b.priceText("€ " + group(Math.round(price / 4.30 / 1000) * 1000, ' ')).currency("EUR");
            return;
        }
        String text = switch (style) {
            case 0 -> group(price, ' ') + " zł" + (rent ? "/mc" : "");
            case 1 -> String.valueOf((long) price);
            case 2 -> group(price, ' ') + ",00 zł";
            default -> group(price, ' ') + " zł" + (!rent && r.nextDouble() < 0.3 ? " do negocjacji" : "");
        };
        b.priceText(text).currency("PLN");
    }

    private static void applyFloor(RawListing.RawListingBuilder b, BaseProperty p, int style, SplittableRandom r) {
        Integer floor = p.floor();
        Integer total = p.totalFloors();
        if (floor == null) {
            b.totalFloorsText(total == null ? null : String.valueOf(total));
            return;
        }
        switch (style) {
            case 0 -> b.floorText((floor == 0 ? "parter" : String.valueOf(floor)) + (total != null ? "/" + total : ""));
            case 1 -> b.floorText(String.valueOf(floor)).totalFloorsText(total == null ? null : String.valueOf(total));
            case 2 -> b.floorText(floor == 0 ? "parter" : floor + " piętro").totalFloorsText(total == null ? null : String.valueOf(total));
            default -> {
                if (r.nextDouble() < 0.4) {
                    return;
                }
                b.floorText(floor == 0 ? "Parter" : String.valueOf(floor)).totalFloorsText(total == null ? null : String.valueOf(total));
            }
        }
    }

    private static String externalId(int style, String portal, long salt) {
        String base36 = Long.toString(Math.abs(salt % 2_000_000_000_000L), 36).toUpperCase(Locale.ROOT);
        String id = switch (style) {
            case 0 -> "ID" + base36;
            case 1 -> "mzn" + Math.abs(salt % 1_000_000_000L);
            case 2 -> String.valueOf(Math.abs(salt % 100_000_000L));
            default -> "CID3-ID" + base36.toLowerCase(Locale.ROOT);
        };
        return PROFILES.containsKey(portal) ? id : id + "-" + portal;
    }

    private static String typeNoun(PropertyType type) {
        return switch (type) {
            case APARTMENT -> "mieszkanie";
            case HOUSE -> "dom";
            case PLOT -> "działka";
            case COMMERCIAL -> "lokal użytkowy";
            case ROOM -> "pokój";
        };
    }

    private static String title(BaseProperty p, int style, SplittableRandom r) {
        String noun = typeNoun(p.type());
        String district = p.district().name();
        String areaInt = String.valueOf(Math.round(p.area()));
        boolean rent = p.transaction() == TransactionType.RENT;
        if (p.type() == PropertyType.APARTMENT) {
            return switch (style) {
                case 0 -> ADJECTIVES[p.adjective()] + " " + p.rooms() + "-pokojowe mieszkanie " + areaText(p.area(), 0) + ", " + district;
                case 1 -> "Mieszkanie " + p.district().cityName() + ", " + district + ", ul. " + p.street() + ", " + areaInt + " m²";
                case 2 -> (rent ? "Wynajmę" : "Sprzedam") + " mieszkanie " + p.rooms() + " pok. " + district + " " + areaInt + " m2";
                default -> ADJECTIVES[(p.adjective() + 1) % 4] + " mieszkanie " + roomsText(p.rooms(), 3).toLowerCase(Locale.ROOT)
                        + " " + district + (p.features().contains("BALCONY") ? " z balkonem" : "")
                        + (p.privateSeller() ? " bez pośredników" : r.nextBoolean() ? " blisko komunikacji" : "");
            };
        }
        String capital = Character.toUpperCase(noun.charAt(0)) + noun.substring(1);
        return switch (style) {
            case 0 -> capital + " " + areaText(p.area(), 0) + ", " + district + (rent ? " – na wynajem" : "");
            case 1 -> capital + " " + p.district().cityName() + ", " + district + ", " + areaInt + " m²";
            case 2 -> (rent ? "Wynajmę " : "Sprzedam ") + noun + " " + areaInt + " m2 " + district;
            default -> capital + " " + district + " " + areaInt + " m2" + (p.privateSeller() ? " – prywatnie" : "");
        };
    }

    private static String description(BaseProperty p) {
        StringBuilder sb = new StringBuilder();
        boolean rent = p.transaction() == TransactionType.RENT;
        sb.append(rent ? "Do wynajęcia " : "Na sprzedaż ").append(typeNoun(p.type()));
        if (p.rooms() != null && p.type() != PropertyType.ROOM) {
            sb.append(" (").append(p.rooms()).append(" pok.)");
        }
        sb.append(" o powierzchni ").append(areaText(p.area(), 0)).append(" w dzielnicy ")
                .append(p.district().name()).append(" (").append(p.district().cityName()).append("), ul. ").append(p.street()).append(". ");
        if (p.floor() != null) {
            sb.append(p.floor() == 0 ? "Lokal na parterze" : "Lokal na " + p.floor() + ". piętrze");
            if (p.totalFloors() != null) {
                sb.append(" w budynku ").append(p.totalFloors()).append("-piętrowym");
            }
            sb.append(". ");
        }
        if (p.semiBasement()) {
            sb.append("Część pomieszczeń w suterenie. ");
        }
        if (p.yearBuilt() != null) {
            sb.append("Rok budowy: ").append(p.yearBuilt()).append(". ");
        }
        if (p.market() == MarketType.PRIMARY) {
            sb.append("Rynek pierwotny, bezpośrednio od dewelopera. ");
        }
        if (!p.features().isEmpty()) {
            sb.append("Atuty: ").append(String.join(", ", p.features().stream().map(FEATURE_LABELS::get).toList())).append(". ");
        }
        sb.append(DemoPriceTable.LOCATION_SENTENCES.get(p.locationSentence())).append(' ');
        if (p.type() != PropertyType.PLOT) {
            sb.append(DemoPriceTable.CONDITION_SENTENCES.get(p.conditionSentence()));
        }
        return sb.toString().trim();
    }

    static String areaText(double area, int style) {
        return switch (style) {
            case 0 -> String.format(Locale.ROOT, "%.1f", area).replace('.', ',').replace(",0", "") + " m²";
            case 1 -> String.format(Locale.ROOT, "%.2f", area) + " m2";
            default -> Math.round(area) + " m²";
        };
    }

    static String roomsText(Integer rooms, int style) {
        if (rooms == null) {
            return null;
        }
        if (style == 0) {
            return String.valueOf(rooms);
        }
        if (style == 3 && rooms == 1) {
            return "Kawalerka";
        }
        String word = rooms == 1 ? "pokój" : rooms < 5 ? "pokoje" : "pokoi";
        return rooms + " " + word;
    }

    private static String yearText(Integer year, int style, SplittableRandom r) {
        if (year == null || (style == 3 && r.nextDouble() < 0.3)) {
            return null;
        }
        return style == 2 ? year + " r." : String.valueOf(year);
    }

    private static String typeText(PropertyType type, int style) {
        String noun = typeNoun(type);
        return switch (style) {
            case 1 -> Character.toUpperCase(noun.charAt(0)) + noun.substring(1);
            case 2 -> noun + (type == PropertyType.APARTMENT ? "a" : "");
            default -> noun;
        };
    }

    private static String transactionText(TransactionType tx, int style) {
        boolean rent = tx == TransactionType.RENT;
        return switch (style) {
            case 0 -> rent ? "wynajem" : "sprzedaż";
            case 1 -> rent ? "Wynajem" : "Sprzedaż";
            case 2 -> rent ? "do wynajęcia" : "na sprzedaż";
            default -> rent ? "Wynajmę" : "Sprzedam";
        };
    }

    private static String marketText(MarketType market, int style, SplittableRandom r) {
        if (market == null || (style == 3 && r.nextBoolean())) {
            return null;
        }
        String base = market == MarketType.PRIMARY ? "pierwotny" : "wtórny";
        return style == 2 ? "rynek " + base : base;
    }

    private static String districtText(BaseProperty p, int style, SplittableRandom r) {
        List<String> aliases = p.district().aliases();
        if (style == 1 && aliases.size() > 1 && r.nextBoolean()) {
            return aliases.get(1 + r.nextInt(aliases.size() - 1));
        }
        return p.district().name();
    }

    private static String streetText(BaseProperty p, int style, SplittableRandom r) {
        return switch (style) {
            case 0 -> "ul. " + p.street() + " " + p.buildingNo();
            case 1 -> p.street();
            case 2 -> "ul. " + p.street();
            default -> r.nextDouble() < 0.4 ? null : p.street();
        };
    }

    private static List<String> featureLabels(List<String> codes, int style) {
        List<String> labels = codes.stream().map(FEATURE_LABELS::get).toList();
        return switch (style) {
            case 1, 3 -> labels.stream().map(l -> Character.toUpperCase(l.charAt(0)) + l.substring(1)).toList();
            case 2 -> labels.isEmpty() ? List.of() : List.of(String.join(", ", labels));
            default -> labels;
        };
    }

    private static List<String> photos(BaseProperty p) {
        int count = 3 + p.index() % 4;
        List<String> urls = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            urls.add("https://picsum.photos/seed/domradar-" + p.index() + "-" + k + "/960/640");
        }
        return urls;
    }

    private static String sellerText(boolean privateSeller, int style) {
        return switch (style) {
            case 0 -> privateSeller ? "prywatny" : "biuro nieruchomości";
            case 1 -> privateSeller ? "oferta prywatna" : null;
            case 2 -> privateSeller ? "właściciel" : "agencja";
            default -> privateSeller ? "Osoba prywatna" : "Firma";
        };
    }

    private static String voivodeship(String citySlug) {
        return switch (citySlug) {
            case "warszawa" -> "mazowieckie";
            case "krakow" -> "małopolskie";
            case "wroclaw" -> "dolnośląskie";
            case "gdansk" -> "pomorskie";
            case "poznan" -> "wielkopolskie";
            case "lodz" -> "łódzkie";
            default -> "";
        };
    }

    private static String group(double value, char separator) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(separator);
        DecimalFormat df = new DecimalFormat("#,##0", symbols);
        return df.format(Math.round(value));
    }
}
