package pl.domradar.search.stats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Statystyki cen per dzielnica (mediana ceny za m²) zmaterializowane w tabeli district_price_stats.
 * Mediana liczona funkcją percentile_cont(0.5) osobno dla każdej kombinacji dzielnica × typ × transakcja.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistrictStatsService {

    private final JdbcTemplate jdbc;

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
}
