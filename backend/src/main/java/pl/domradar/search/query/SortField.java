package pl.domradar.search.query;

import java.util.Arrays;
import java.util.Optional;

/**
 * Biała lista pól sortowania (nazwa w API → wyrażenie SQL). Dzięki temu parametr sort nigdy nie trafia do SQL wprost.
 * {@code invert} = kierunek "asc" oznacza malejące wyrażenie SQL (np. dni na rynku rosnąco = najnowsze najpierw).
 */
public enum SortField {
    RELEVANCE("relevance", "relevance", false, "desc"),
    PRICE("price", "p.current_price", false, "asc"),
    PRICE_PER_M2("pricePerM2", "p.price_per_m2", false, "asc"),
    AREA("area", "p.area", false, "desc"),
    ADDED_AT("addedAt", "p.first_seen_at", false, "desc"),
    PRICE_CHANGED_AT("priceChangedAt", "p.last_price_change_at", false, "desc"),
    PRICE_DROP("priceDrop", "p.price_drop_percent", false, "desc"),
    DAYS_ON_MARKET("daysOnMarket", "p.first_seen_at", true, "asc"),
    DISTANCE("distance", "distance_m", false, "asc"),
    VS_MEDIAN("vsMedian", "vs_median_pct", false, "asc");

    private final String apiName;
    private final String sql;
    private final boolean invert;
    private final String defaultDirection;

    SortField(String apiName, String sql, boolean invert, String defaultDirection) {
        this.apiName = apiName;
        this.sql = sql;
        this.invert = invert;
        this.defaultDirection = defaultDirection;
    }

    public String apiName() {
        return apiName;
    }

    public String sql() {
        return sql;
    }

    public boolean invert() {
        return invert;
    }

    public String defaultDirection() {
        return defaultDirection;
    }

    public static Optional<SortField> fromApiName(String name) {
        return Arrays.stream(values()).filter(f -> f.apiName.equalsIgnoreCase(name)).findFirst();
    }

    public static String allowedNames() {
        return String.join(", ", Arrays.stream(values()).map(SortField::apiName).toList());
    }
}
