package pl.domradar.search;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pl.domradar.property.MarketType;
import pl.domradar.property.PropertyStatus;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;
import pl.domradar.search.dto.MapPointDto;
import pl.domradar.search.dto.PropertySummaryDto;
import pl.domradar.search.dto.SearchFacets.FacetValue;
import pl.domradar.search.dto.SearchFacets.HistogramBucket;
import pl.domradar.search.query.SearchSqlBuilder;
import pl.domradar.search.query.SearchSqlBuilder.Dimension;
import pl.domradar.search.query.SortSpec;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Zapytania wyszukiwarki w natywnym SQL (PostgreSQL + PostGIS + pg_trgm), korzystające z indeksów
 * GIN (tsvector, trigramy), GiST (geografia) i złożonych btree. Statystyki dzielnic dołączane z
 * tabeli district_price_stats, co pozwala sortować po "% poniżej mediany dzielnicy".
 */
@Repository
public class SearchRepository {

    private static final String FROM = """
            FROM property p
            JOIN city c ON c.id = p.city_id
            LEFT JOIN district d ON d.id = p.district_id
            LEFT JOIN district_price_stats st ON st.district_id = p.district_id
                 AND st.property_type = p.property_type AND st.transaction_type = p.transaction_type
            """;

    private static final String VS_MEDIAN = """
            CASE WHEN st.median_price_per_m2 > 0 AND p.price_per_m2 IS NOT NULL
                 THEN ROUND((p.price_per_m2 - st.median_price_per_m2) * 100 / st.median_price_per_m2, 1) END""";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public SearchRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public long count(SearchSqlBuilder b) {
        Long count = jdbc.queryForObject("SELECT count(*) FROM property p WHERE " + b.where(), b.params(), Long.class);
        return count == null ? 0 : count;
    }

    public List<PropertySummaryDto> page(SearchSqlBuilder b, SortSpec sort, int limit, long offset) {
        String relevance = b.relevanceExpression();
        String sql = """
                SELECT p.id, p.title, p.property_type, p.transaction_type, p.market_type, p.city_id, c.name AS city_name,
                       p.district_id, d.name AS district_name, p.street,
                       ST_Y(p.location::geometry) AS lat, ST_X(p.location::geometry) AS lon,
                       p.area, p.rooms, p.floor, p.total_floors, p.year_built, p.current_price, p.price_per_m2,
                       p.price_dropped, p.price_drop_percent, p.last_price_change_at, p.active_source_count,
                       p.first_seen_at, p.status, p.main_photo_url, st.median_price_per_m2,
                       %s AS vs_median_pct, %s AS relevance, %s AS distance_m
                %s
                WHERE %s
                ORDER BY %s
                LIMIT :limit OFFSET :offset
                """.formatted(VS_MEDIAN, relevance, b.distanceExpression(), FROM, b.where(),
                sort.toSql(b.hasRelevance(), b.hasDistance()));
        MapSqlParameterSource params = b.params().addValue("limit", limit).addValue("offset", offset);
        return jdbc.query(sql, params, summaryMapper());
    }

    public List<MapPointDto> mapPoints(SearchSqlBuilder b, int limit) {
        String sql = """
                SELECT p.id, ST_Y(p.location::geometry) AS lat, ST_X(p.location::geometry) AS lon,
                       p.current_price, p.price_per_m2, p.rooms, p.area, p.title
                FROM property p
                WHERE p.location IS NOT NULL AND %s
                ORDER BY p.first_seen_at DESC, p.id DESC
                LIMIT :limit
                """.formatted(b.where());
        return jdbc.query(sql, b.params().addValue("limit", limit), (rs, i) -> new MapPointDto(
                rs.getLong("id"), rs.getDouble("lat"), rs.getDouble("lon"), rs.getBigDecimal("current_price"),
                rs.getBigDecimal("price_per_m2"), (Integer) rs.getObject("rooms"), rs.getBigDecimal("area"),
                rs.getString("title")));
    }

    /** Identyfikatory spośród podanych, które spełniają kryteria — używane przez dopasowanie alertów. */
    public List<Long> matchingIds(SearchSqlBuilder b, Collection<Long> candidateIds) {
        if (candidateIds.isEmpty()) {
            return List.of();
        }
        String sql = "SELECT p.id FROM property p WHERE p.id IN (:candidateIds) AND " + b.where() + " ORDER BY p.id";
        return jdbc.queryForList(sql, b.params().addValue("candidateIds", candidateIds), Long.class);
    }

    public List<FacetValue> districtFacet(SearchSqlBuilder b) {
        String sql = """
                SELECT p.district_id AS k, d.name AS label, count(*) AS cnt
                FROM property p JOIN district d ON d.id = p.district_id
                WHERE %s
                GROUP BY p.district_id, d.name ORDER BY cnt DESC, d.name LIMIT 60
                """.formatted(b.where(Dimension.DISTRICT));
        return jdbc.query(sql, b.params(), facetMapper());
    }

    public List<FacetValue> roomsFacet(SearchSqlBuilder b) {
        String sql = """
                SELECT LEAST(p.rooms, 5) AS k, CASE WHEN p.rooms >= 5 THEN '5+' ELSE CAST(p.rooms AS TEXT) END AS label, count(*) AS cnt
                FROM property p
                WHERE p.rooms IS NOT NULL AND %s
                GROUP BY 1, 2 ORDER BY 1
                """.formatted(b.where(Dimension.ROOMS));
        return jdbc.query(sql, b.params(), facetMapper());
    }

    public List<FacetValue> typeFacet(SearchSqlBuilder b) {
        String sql = """
                SELECT p.property_type AS k, p.property_type AS label, count(*) AS cnt
                FROM property p WHERE %s
                GROUP BY 1, 2 ORDER BY cnt DESC
                """.formatted(b.where(Dimension.TYPE));
        return jdbc.query(sql, b.params(), facetMapper());
    }

    public List<FacetValue> sourceFacet(SearchSqlBuilder b) {
        String sql = """
                SELECT so.code AS k, so.name AS label, count(DISTINCT p.id) AS cnt
                FROM property p
                JOIN offer o ON o.property_id = p.id AND (o.active OR p.status = 'ARCHIVED')
                JOIN source so ON so.id = o.source_id
                WHERE %s
                GROUP BY so.code, so.name ORDER BY cnt DESC
                """.formatted(b.where(Dimension.SOURCE));
        return jdbc.query(sql, b.params(), facetMapper());
    }

    /**
     * Histogram cen: granice od 2. do 98. percentyla (odporne na wartości odstające), "ładny" krok
     * (1/2/2.5/5 × 10^n); wartości spoza zakresu trafiają do skrajnych kubełków.
     */
    public List<HistogramBucket> priceHistogram(SearchSqlBuilder b) {
        String where = b.where(Dimension.PRICE);
        Map<String, Object> bounds = jdbc.queryForMap("""
                SELECT percentile_cont(0.02) WITHIN GROUP (ORDER BY p.current_price) AS lo,
                       percentile_cont(0.98) WITHIN GROUP (ORDER BY p.current_price) AS hi
                FROM property p WHERE p.current_price IS NOT NULL AND %s
                """.formatted(where), b.params());
        if (bounds.get("lo") == null || bounds.get("hi") == null) {
            return List.of();
        }
        double lo = ((Number) bounds.get("lo")).doubleValue();
        double hi = ((Number) bounds.get("hi")).doubleValue();
        double step = niceStep(Math.max(hi - lo, 1) / 10);
        double start = Math.floor(lo / step) * step;
        int buckets = (int) Math.max(1, Math.min(15, Math.ceil((hi - start) / step)));
        int last = buckets - 1;
        MapSqlParameterSource params = b.params().addValue("hStart", start).addValue("hStep", step).addValue("hLast", last);
        Map<Integer, Long> counts = new HashMap<>();
        jdbc.query("""
                SELECT CAST(LEAST(GREATEST(FLOOR((p.current_price - :hStart) / :hStep), 0), :hLast) AS INT) AS bucket, count(*) AS cnt
                FROM property p WHERE p.current_price IS NOT NULL AND %s
                GROUP BY 1
                """.formatted(where), params, rs -> {
            counts.put(rs.getInt("bucket"), rs.getLong("cnt"));
        });
        List<HistogramBucket> result = new ArrayList<>(buckets);
        for (int i = 0; i < buckets; i++) {
            result.add(new HistogramBucket(
                    BigDecimal.valueOf(start + i * step).setScale(0, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(start + (i + 1) * step).setScale(0, RoundingMode.HALF_UP),
                    counts.getOrDefault(i, 0L)));
        }
        return result;
    }

    static double niceStep(double raw) {
        double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
        double normalized = raw / magnitude;
        double nice = normalized <= 1 ? 1 : normalized <= 2 ? 2 : normalized <= 2.5 ? 2.5 : normalized <= 5 ? 5 : 10;
        return nice * magnitude;
    }

    private static RowMapper<FacetValue> facetMapper() {
        return (rs, i) -> new FacetValue(rs.getString("k"), rs.getString("label"), rs.getLong("cnt"));
    }

    private RowMapper<PropertySummaryDto> summaryMapper() {
        Instant now = clock.instant();
        return (rs, i) -> {
            Instant firstSeen = instant(rs, "first_seen_at");
            Object distance = rs.getObject("distance_m");
            return new PropertySummaryDto(
                    rs.getLong("id"),
                    rs.getString("title"),
                    PropertyType.valueOf(rs.getString("property_type")),
                    TransactionType.valueOf(rs.getString("transaction_type")),
                    enumOrNull(MarketType.class, rs.getString("market_type")),
                    rs.getLong("city_id"),
                    rs.getString("city_name"),
                    (Long) rs.getObject("district_id"),
                    rs.getString("district_name"),
                    rs.getString("street"),
                    (Double) rs.getObject("lat"),
                    (Double) rs.getObject("lon"),
                    rs.getBigDecimal("area"),
                    (Integer) rs.getObject("rooms"),
                    (Integer) rs.getObject("floor"),
                    (Integer) rs.getObject("total_floors"),
                    (Integer) rs.getObject("year_built"),
                    rs.getBigDecimal("current_price"),
                    rs.getBigDecimal("price_per_m2"),
                    rs.getBoolean("price_dropped"),
                    rs.getBigDecimal("price_drop_percent"),
                    instant(rs, "last_price_change_at"),
                    rs.getInt("active_source_count"),
                    firstSeen,
                    firstSeen == null ? 0 : Math.max(0, Duration.between(firstSeen, now).toDays()),
                    PropertyStatus.valueOf(rs.getString("status")),
                    rs.getString("main_photo_url"),
                    rs.getBigDecimal("median_price_per_m2"),
                    rs.getBigDecimal("vs_median_pct"),
                    distance == null ? null : ((Number) distance).doubleValue());
        };
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toInstant();
    }

    private static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }
}
