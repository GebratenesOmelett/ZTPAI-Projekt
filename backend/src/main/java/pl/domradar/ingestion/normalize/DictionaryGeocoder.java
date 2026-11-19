package pl.domradar.ingestion.normalize;

import org.springframework.stereotype.Component;
import pl.domradar.common.util.TextNormalizer;
import pl.domradar.ingestion.normalize.DictionaryLookup.CityRef;
import pl.domradar.ingestion.normalize.DictionaryLookup.DistrictRef;

/**
 * Geokoder offline: centroid dzielnicy (lub miasta) przesunięty deterministycznie na podstawie nazwy ulicy.
 * Ta sama ulica w tej samej dzielnicy daje zawsze ten sam punkt, co pomaga w deduplikacji ogłoszeń bez współrzędnych.
 * Świadomie nie korzystamy z zewnętrznych geokoderów (limity, regulaminy, prywatność) — patrz README.
 */
@Component
public class DictionaryGeocoder implements Geocoder {

    private static final double MAX_OFFSET_DEG = 0.006;

    @Override
    public GeoPoint geocode(CityRef city, DistrictRef district, String street) {
        double lat = district != null ? district.lat() : city.lat();
        double lon = district != null ? district.lon() : city.lon();
        String streetKey = TextNormalizer.key(street == null ? "" : street.replaceAll("(?i)^(ul\\.|al\\.|pl\\.|os\\.)\\s*", "")
                .replaceAll("\\s+\\d+[a-zA-Z]?(/\\d+)?$", ""));
        if (!streetKey.isEmpty()) {
            int h = streetKey.hashCode();
            lat += ((h & 0xFFFF) / 65535.0 - 0.5) * 2 * MAX_OFFSET_DEG;
            lon += (((h >>> 16) & 0xFFFF) / 65535.0 - 0.5) * 2 * MAX_OFFSET_DEG * 1.6;
        }
        return new GeoPoint(lat, lon);
    }
}
