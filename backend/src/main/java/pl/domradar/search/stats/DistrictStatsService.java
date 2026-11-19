package pl.domradar.search.stats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.common.config.CacheConfig;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Statystyki cen per dzielnica (mediana ceny za m²) zmaterializowane w tabeli district_price_stats.
 * Mediana liczona funkcją percentile_cont(0.5) osobno dla każdej kombinacji dzielnica × typ × transakcja.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistrictStatsService {

    public record MedianComparison(BigDecimal districtMedianPricePerM2, BigDecimal vsMedianPercent, int sampleSize) {
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    /** REQUIRES_NEW: wywoływane także z listenerów AFTER_COMMIT, gdzie poprzednia transakcja jest już zamknięta. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refresh() {
        jdbc.update("DELETE FROM district_price_stats");
        int rows = jdbc.update("""
                INSERT INTO district_price_stats (district_id, property_type, transaction_type,
                                                  median_price_per_m2, avg_price_per_m2, listing_count, updated_at)
                SELECT district_id, property_type, transaction_type,
                       ROUND(CAST(percentile_cont(0.5) WITHIN GROUP (ORDER BY price_per_m2) AS NUMERIC), 2),
                       ROUND(AVG(price_per_m2), 2), COUNT(*), now()
                FROM property
                WHERE status = 'ACTIVE' AND district_id IS NOT NULL AND price_per_m2 IS NOT NULL
                GROUP BY district_id, property_type, transaction_type
                """);
        log.info("District price statistics refreshed ({} groups)", rows);
    }

    @Cacheable(cacheNames = CacheConfig.DISTRICT_STATS, key = "#cityId + ':' + #propertyType + ':' + #transactionType")
    @Transactional(readOnly = true)
    public List<DistrictStatsDto> list(Long cityId, PropertyType propertyType, TransactionType transactionType) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cityId", cityId, java.sql.Types.BIGINT)
                .addValue("type", propertyType == null ? null : propertyType.name(), java.sql.Types.VARCHAR)
                .addValue("tx", transactionType == null ? null : transactionType.name(), java.sql.Types.VARCHAR);
        return namedJdbc.query("""
                SELECT s.district_id, d.name AS district, c.id AS city_id, c.name AS city, s.property_type, s.transaction_type,
                       s.median_price_per_m2, s.avg_price_per_m2, s.listing_count, s.updated_at
                FROM district_price_stats s
                JOIN district d ON d.id = s.district_id
                JOIN city c ON c.id = d.city_id
                WHERE (CAST(:cityId AS BIGINT) IS NULL OR c.id = :cityId)
                  AND (CAST(:type AS VARCHAR) IS NULL OR s.property_type = :type)
                  AND (CAST(:tx AS VARCHAR) IS NULL OR s.transaction_type = :tx)
                ORDER BY c.name, s.median_price_per_m2 DESC
                """, params, (rs, i) -> new DistrictStatsDto(
                rs.getLong("district_id"), rs.getString("district"), rs.getLong("city_id"), rs.getString("city"),
                PropertyType.valueOf(rs.getString("property_type")), TransactionType.valueOf(rs.getString("transaction_type")),
                rs.getBigDecimal("median_price_per_m2"), rs.getBigDecimal("avg_price_per_m2"), rs.getInt("listing_count"),
                rs.getTimestamp("updated_at").toInstant()));
    }

    /** Porównanie ceny za m² do mediany dzielnicy dla nieruchomości o danym typie i transakcji. */
    @Transactional(readOnly = true)
    public Optional<MedianComparison> compare(Long districtId, PropertyType type, TransactionType tx, BigDecimal pricePerM2) {
        if (districtId == null || pricePerM2 == null) {
            return Optional.empty();
        }
        return jdbc.query("""
                        SELECT median_price_per_m2, listing_count FROM district_price_stats
                        WHERE district_id = ? AND property_type = ? AND transaction_type = ?
                        """, rs -> rs.next() ? Optional.of(build(rs.getBigDecimal(1), rs.getInt(2), pricePerM2)) : Optional.<MedianComparison>empty(),
                districtId, type.name(), tx.name());
    }

    static MedianComparison build(BigDecimal median, int sample, BigDecimal pricePerM2) {
        if (median == null || median.signum() <= 0) {
            return new MedianComparison(median, null, sample);
        }
        BigDecimal pct = pricePerM2.subtract(median).multiply(BigDecimal.valueOf(100)).divide(median, 1, RoundingMode.HALF_UP);
        return new MedianComparison(median, pct, sample);
    }
}
