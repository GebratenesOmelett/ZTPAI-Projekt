package pl.domradar.ingestion.adapter.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.ingestion.IngestionProperties;
import pl.domradar.property.MarketType;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;
import pl.domradar.property.dictionary.District;
import pl.domradar.property.dictionary.DistrictRepository;

import java.util.*;

/**
 * Deterministyczne "uniwersum" nieruchomości, z którego DemoAdapter generuje ogłoszenia różnych portali.
 * Ten sam seed daje zawsze te same nieruchomości; numer importu (run) steruje zmianami cen
 * oraz pojawianiem się i znikaniem ogłoszeń.
 */
@Slf4j
@Component
public class DemoUniverse {

    record DistrictInfo(String cityName, String citySlug, String name, String slug, List<String> aliases,
                        double lat, double lon, int indexInCity) {
    }

    record BaseProperty(int index, DistrictInfo district, String street, int buildingNo, double lat, double lon,
                        PropertyType type, TransactionType transaction, MarketType market, double area,
                        Integer rooms, Integer floor, Integer totalFloors, Integer yearBuilt, double basePrice,
                        List<String> features, int appearsAt, Integer disappearsAt, boolean privateSeller,
                        boolean semiBasement, int locationSentence, int conditionSentence, int adjective) {

        boolean visibleAt(int run) {
            return appearsAt <= run && (disappearsAt == null || run < disappearsAt);
        }

        /** Część ogłoszeń znika po kilku importach (sprzedane / zdjęte z portalu). */
        BaseProperty withDisappearance(SplittableRandom r) {
            Integer disappears = r.nextDouble() < 0.22 ? appearsAt + 1 + r.nextInt(5) : null;
            return new BaseProperty(index, district, street, buildingNo, lat, lon, type, transaction, market, area,
                    rooms, floor, totalFloors, yearBuilt, basePrice, features, appearsAt, disappears, privateSeller,
                    semiBasement, locationSentence, conditionSentence, adjective);
        }
    }

    private final DistrictRepository districtRepository;
    private final IngestionProperties properties;
    private volatile List<BaseProperty> cache;

    public DemoUniverse(DistrictRepository districtRepository, IngestionProperties properties) {
        this.districtRepository = districtRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<BaseProperty> properties() {
        List<BaseProperty> local = cache;
        if (local == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = build();
                }
                local = cache;
            }
        }
        return local;
    }

    long seed() {
        return properties.demo().seed();
    }

    /** Cena nieruchomości w danym imporcie: cena bazowa przemnożona przez deterministyczne zmiany w kolejnych importach. */
    double priceAt(BaseProperty p, int run) {
        double price = p.basePrice();
        for (int k = 2; k <= run; k++) {
            SplittableRandom rk = new SplittableRandom(mix(seed(), p.index(), k, 77));
            double u = rk.nextDouble();
            if (u < 0.12) {
                price *= 0.92 + rk.nextDouble() * 0.06;
            } else if (u < 0.16) {
                price *= 1.01 + rk.nextDouble() * 0.02;
            }
        }
        return price;
    }

    private List<BaseProperty> build() {
        Map<String, List<DistrictInfo>> byCity = new TreeMap<>();
        for (District d : districtRepository.findAllByOrderByIdAsc()) {
            List<DistrictInfo> list = byCity.computeIfAbsent(d.getCity().getSlug(), k -> new ArrayList<>());
            list.add(new DistrictInfo(d.getCity().getName(), d.getCity().getSlug(), d.getName(), d.getSlug(),
                    d.aliasList(), d.getLat(), d.getLon(), list.size()));
        }
        if (byCity.isEmpty()) {
            return List.of();
        }
        int size = Math.max(10, properties.demo().universeSize());
        List<BaseProperty> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            SplittableRandom r = new SplittableRandom(mix(seed(), i, 1, 1));
            BaseProperty prev = result.isEmpty() ? null : result.getLast();
            boolean sibling = prev != null && prev.type() == PropertyType.APARTMENT && r.nextDouble() < 0.10;
            result.add(sibling ? sibling(i, prev, r) : fresh(i, byCity, r));
        }
        log.info("Demo universe built: {} base properties", result.size());
        return result;
    }

    private BaseProperty fresh(int i, Map<String, List<DistrictInfo>> byCity, SplittableRandom r) {
        String citySlug = pickCity(byCity.keySet(), r);
        List<DistrictInfo> districts = byCity.get(citySlug);
        DistrictInfo d = districts.get(r.nextInt(districts.size()));
        TransactionType tx = r.nextDouble() < 0.8 ? TransactionType.SALE : TransactionType.RENT;
        PropertyType type = pickType(tx, r);

        Integer rooms;
        Integer floor;
        Integer totalFloors;
        double area;
        switch (type) {
            case HOUSE -> {
                area = 90 + r.nextDouble() * 170;
                rooms = 3 + r.nextInt(5);
                floor = null;
                totalFloors = 1 + r.nextInt(3);
            }
            case PLOT -> {
                area = 400 + r.nextDouble() * 1200;
                rooms = null;
                floor = null;
                totalFloors = null;
            }
            case COMMERCIAL -> {
                area = 30 + r.nextDouble() * 190;
                rooms = 1 + r.nextInt(4);
                totalFloors = 2 + r.nextInt(5);
                floor = r.nextInt(3);
            }
            case ROOM -> {
                area = 9 + r.nextDouble() * 11;
                rooms = 1;
                totalFloors = pick(r, 3, 4, 5, 10);
                floor = r.nextInt(totalFloors + 1);
            }
            default -> {
                rooms = weightedRooms(r);
                double base = switch (rooms) {
                    case 1 -> 28;
                    case 2 -> 46;
                    case 3 -> 62;
                    case 4 -> 80;
                    default -> 105;
                };
                area = base * (0.85 + r.nextDouble() * 0.35);
                totalFloors = pick(r, 3, 4, 5, 6, 8, 10, 12, 16);
                floor = r.nextInt(totalFloors + 1);
            }
        }
        area = Math.round(area * 10) / 10.0;
        Integer year = type == PropertyType.PLOT ? null : pickYear(r);
        MarketType market = year != null && year >= 2025 && r.nextDouble() < 0.8 ? MarketType.PRIMARY : MarketType.SECONDARY;

        List<String> streets = DemoPriceTable.STREETS.getOrDefault(citySlug, List.of("Główna"));
        String street = streets.get((d.indexInCity() * 3 + r.nextInt(3)) % streets.size());
        double lat = d.lat() + clamp(r.nextGaussian(), 2.5) * 0.009;
        double lon = d.lon() + clamp(r.nextGaussian(), 2.5) * 0.014;

        double ppm2 = DemoPriceTable.PRICE_PER_M2.getOrDefault(citySlug + "/" + d.slug(), 10000) * (0.85 + r.nextDouble() * 0.33);
        if (market == MarketType.PRIMARY) {
            ppm2 *= 1.05;
        }
        if (floor != null && floor == 0) {
            ppm2 *= 0.96;
        }
        if (year != null && year > 1945 && year < 1990) {
            ppm2 *= 0.93;
        }
        double price = priceFor(type, tx, area, ppm2, r);
        return new BaseProperty(i, d, street, 1 + r.nextInt(120), lat, lon, type, tx, market, area, rooms, floor,
                totalFloors, year, price, features(type, tx, market, floor, totalFloors, r),
                appearsAt(r), null, r.nextDouble() < 0.3, floor != null && floor == 0 && r.nextDouble() < 0.2,
                r.nextInt(DemoPriceTable.LOCATION_SENTENCES.size()), r.nextInt(DemoPriceTable.CONDITION_SENTENCES.size()),
                r.nextInt(4)).withDisappearance(r);
    }

    /** Inne mieszkanie w tym samym budynku — celowo podobne, ale NIE powinno zostać scalone. */
    private BaseProperty sibling(int i, BaseProperty prev, SplittableRandom r) {
        int floor;
        do {
            floor = r.nextInt(prev.totalFloors() + 1);
        } while (prev.floor() != null && floor == prev.floor());
        double factor = r.nextBoolean() ? 1.12 + r.nextDouble() * 0.23 : 0.70 + r.nextDouble() * 0.18;
        double area = Math.round(prev.area() * factor * 10) / 10.0;
        Integer rooms = r.nextDouble() < 0.6 ? prev.rooms() : Math.max(1, prev.rooms() + (factor > 1 ? 1 : -1));
        double price = prev.basePrice() / prev.area() * area * (0.98 + r.nextDouble() * 0.05);
        price = prev.transaction() == TransactionType.SALE ? Math.round(price / 1000) * 1000 : Math.round(price / 50) * 50;
        return new BaseProperty(i, prev.district(), prev.street(), prev.buildingNo(),
                prev.lat() + (r.nextDouble() - 0.5) * 0.0002, prev.lon() + (r.nextDouble() - 0.5) * 0.0002,
                PropertyType.APARTMENT, prev.transaction(), prev.market(), area, rooms, floor, prev.totalFloors(),
                prev.yearBuilt(), price, features(PropertyType.APARTMENT, prev.transaction(), prev.market(), floor,
                prev.totalFloors(), r), appearsAt(r), null, r.nextDouble() < 0.3, false,
                r.nextInt(DemoPriceTable.LOCATION_SENTENCES.size()), r.nextInt(DemoPriceTable.CONDITION_SENTENCES.size()),
                r.nextInt(4)).withDisappearance(r);
    }

    private static double priceFor(PropertyType type, TransactionType tx, double area, double ppm2, SplittableRandom r) {
        double price;
        if (tx == TransactionType.RENT) {
            price = switch (type) {
                case ROOM -> 900 + r.nextInt(1300);
                case COMMERCIAL -> area * ppm2 / 180;
                case HOUSE -> area * ppm2 * 0.75 / 260;
                default -> area * ppm2 / 230;
            };
            return Math.max(500, Math.round(price / 50) * 50);
        }
        price = switch (type) {
            case HOUSE -> area * ppm2 * 0.75;
            case PLOT -> area * ppm2 * 0.08;
            case COMMERCIAL -> area * ppm2 * 0.9;
            default -> area * ppm2;
        };
        return Math.round(price / 1000) * 1000;
    }

    private static List<String> features(PropertyType type, TransactionType tx, MarketType market, Integer floor,
                                         Integer totalFloors, SplittableRandom r) {
        List<String> f = new ArrayList<>();
        if (type == PropertyType.PLOT) {
            return f;
        }
        boolean apartment = type == PropertyType.APARTMENT || type == PropertyType.ROOM;
        boolean top = floor != null && totalFloors != null && floor.equals(totalFloors);
        boolean ground = floor != null && floor == 0;
        if (apartment && r.nextDouble() < 0.6) f.add("BALCONY");
        if (r.nextDouble() < (top || type == PropertyType.HOUSE ? 0.4 : 0.08)) f.add("TERRACE");
        if (r.nextDouble() < (type == PropertyType.HOUSE ? 0.9 : ground ? 0.4 : 0.02)) f.add("GARDEN");
        if (r.nextDouble() < (type == PropertyType.HOUSE ? 0.7 : market == MarketType.PRIMARY ? 0.6 : 0.3)) f.add("GARAGE");
        if (apartment && r.nextDouble() < (totalFloors != null && totalFloors > 4 ? 0.85 : 0.1)) f.add("ELEVATOR");
        if (r.nextDouble() < 0.4) f.add("BASEMENT");
        if (apartment && r.nextDouble() < 0.3) f.add("STORAGE");
        if (r.nextDouble() < 0.12) f.add("AIR_CONDITIONING");
        if (r.nextDouble() < (tx == TransactionType.RENT ? 0.7 : 0.1)) f.add("FURNISHED");
        if (r.nextDouble() < 0.25) f.add("MONITORING");
        if (apartment && r.nextDouble() < 0.08) f.add("LOGGIA");
        if (apartment && r.nextDouble() < 0.35) f.add("SEPARATE_KITCHEN");
        return f;
    }

    private static int appearsAt(SplittableRandom r) {
        return r.nextDouble() < 0.75 ? 1 : 2 + r.nextInt(6);
    }

    private static String pickCity(Set<String> available, SplittableRandom r) {
        double u = r.nextDouble();
        double acc = 0;
        String last = null;
        for (String slug : available) {
            acc += DemoPriceTable.CITY_WEIGHTS.getOrDefault(slug, 0.05);
            last = slug;
            if (u < acc) {
                return slug;
            }
        }
        return last;
    }

    private static PropertyType pickType(TransactionType tx, SplittableRandom r) {
        double u = r.nextDouble();
        if (tx == TransactionType.SALE) {
            return u < 0.85 ? PropertyType.APARTMENT : u < 0.93 ? PropertyType.HOUSE : u < 0.97 ? PropertyType.COMMERCIAL : PropertyType.PLOT;
        }
        return u < 0.85 ? PropertyType.APARTMENT : u < 0.93 ? PropertyType.ROOM : PropertyType.COMMERCIAL;
    }

    private static int weightedRooms(SplittableRandom r) {
        double u = r.nextDouble();
        return u < 0.15 ? 1 : u < 0.50 ? 2 : u < 0.80 ? 3 : u < 0.95 ? 4 : 5;
    }

    private static int pickYear(SplittableRandom r) {
        double u = r.nextDouble();
        if (u < 0.10) return 1900 + r.nextInt(40);
        if (u < 0.45) return 1950 + r.nextInt(40);
        if (u < 0.75) return 1990 + r.nextInt(25);
        return 2015 + r.nextInt(12);
    }

    private static int pick(SplittableRandom r, int... values) {
        return values[r.nextInt(values.length)];
    }

    private static double clamp(double v, double limit) {
        return Math.max(-limit, Math.min(limit, v));
    }

    /** Mieszanie wielu wartości w jeden 64-bitowy seed (finalizer SplitMix64). */
    static long mix(long... parts) {
        long h = 0x9E3779B97F4A7C15L;
        for (long p : parts) {
            h ^= p + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
            h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
            h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
            h ^= h >>> 31;
        }
        return h;
    }

    static double unit(long... parts) {
        return (mix(parts) >>> 11) * 0x1.0p-53;
    }
}
