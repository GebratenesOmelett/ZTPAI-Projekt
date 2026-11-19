package pl.domradar.ingestion.dedup;

import pl.domradar.ingestion.normalize.NormalizedListing;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reguła biznesowa nr 1 — scoring podobieństwa ogłoszenia do istniejącej nieruchomości (0–100 pkt).
 *
 * <p>Warunki konieczne (inaczej 0 pkt): to samo miasto, typ nieruchomości i rodzaj transakcji.</p>
 * <table>
 *   <tr><th>Kryterium</th><th>Punkty</th></tr>
 *   <tr><td>dzielnica równa (brak danych: połowa)</td><td>10</td></tr>
 *   <tr><td>metraż ±2%</td><td>20</td></tr>
 *   <tr><td>liczba pokoi równa (brak danych: 7, różna: −15)</td><td>15</td></tr>
 *   <tr><td>piętro równe (brak danych: 5, różne: −25)</td><td>10</td></tr>
 *   <tr><td>odległość &lt; 150 m (współrzędne z geokodera: 10)</td><td>20</td></tr>
 *   <tr><td>cena ±7%</td><td>10</td></tr>
 *   <tr><td>podobieństwo tytułu (trigramy) × 15</td><td>0–15</td></tr>
 * </table>
 * Kary za sprzeczne piętro/pokoje chronią przed scaleniem identycznych mieszkań z różnych pięter
 * tego samego budynku (typowe w nowych inwestycjach).
 */
public final class DedupScorer {

    public static final double AREA_TOLERANCE = 0.02;
    public static final double PRICE_TOLERANCE = 0.07;
    public static final double MAX_DISTANCE_METERS = 150.0;

    public record Score(int total, Map<String, Integer> parts) {

        public boolean matches(int threshold) {
            return total >= threshold;
        }
    }

    private DedupScorer() {
    }

    public static Score score(NormalizedListing listing, PropertyCandidate candidate) {
        Map<String, Integer> parts = new LinkedHashMap<>();
        if (!Objects.equals(listing.cityId(), candidate.cityId())
                || listing.propertyType() != candidate.propertyType()
                || listing.transactionType() != candidate.transactionType()) {
            parts.put("hardMismatch", 0);
            return new Score(0, parts);
        }
        parts.put("district", listing.districtId() == null || candidate.districtId() == null ? 5
                : listing.districtId().equals(candidate.districtId()) ? 10 : 0);
        parts.put("area", withinTolerance(listing.area(), candidate.area(), AREA_TOLERANCE) ? 20 : 0);
        parts.put("rooms", listing.rooms() == null || candidate.rooms() == null ? 7
                : listing.rooms().equals(candidate.rooms()) ? 15 : -15);
        parts.put("floor", listing.floor() == null || candidate.floor() == null ? 5
                : listing.floor().equals(candidate.floor()) ? 10 : -25);
        double distance = candidate.lat() == null || candidate.lon() == null ? Double.MAX_VALUE
                : haversineMeters(listing.lat(), listing.lon(), candidate.lat(), candidate.lon());
        // współrzędne wyznaczone geokoderem są przybliżone — kryterium neutralne (połowa punktów)
        parts.put("distance", listing.geocoded() ? 10 : distance < MAX_DISTANCE_METERS ? 20 : 0);
        parts.put("price", withinTolerance(listing.price(), candidate.currentPrice(), PRICE_TOLERANCE) ? 10 : 0);
        double titleSimilarity = candidate.titles() == null ? 0 : candidate.titles().stream()
                .mapToDouble(t -> TrigramSimilarity.similarity(listing.title(), t))
                .max().orElse(0);
        parts.put("title", (int) Math.round(titleSimilarity * 15));
        int total = parts.values().stream().mapToInt(Integer::intValue).sum();
        return new Score(Math.max(0, Math.min(100, total)), parts);
    }

    static boolean withinTolerance(BigDecimal a, BigDecimal b, double tolerance) {
        if (a == null || b == null || a.signum() <= 0 || b.signum() <= 0) {
            return false;
        }
        BigDecimal max = a.max(b);
        BigDecimal diff = a.subtract(b).abs();
        return diff.divide(max, MathContext.DECIMAL64).doubleValue() <= tolerance + 1e-9;
    }

    public static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
