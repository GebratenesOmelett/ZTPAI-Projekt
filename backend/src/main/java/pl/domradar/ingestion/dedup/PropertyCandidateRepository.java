package pl.domradar.ingestion.dedup;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import pl.domradar.ingestion.normalize.NormalizedListing;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;
import java.sql.Array;
import java.util.Arrays;
import java.util.List;

/**
 * Wyszukiwanie kandydatów do deduplikacji: te same miasto/typ/transakcja, metraż ±10%,
 * w promieniu N metrów (indeks GiST) lub w tej samej dzielnicy. Pomijamy nieruchomości,
 * które mają już aktywną ofertę z tego samego portalu (portal rzadko wystawia to samo mieszkanie dwa razy).
 */
@Repository
public class PropertyCandidateRepository {

    private static final String SQL = """
            SELECT p.id, p.city_id, p.district_id, p.property_type, p.transaction_type, p.area, p.rooms, p.floor,
                   ST_Y(p.location::geometry) AS lat, ST_X(p.location::geometry) AS lon, p.current_price,
                   ARRAY(SELECT o.title FROM offer o WHERE o.property_id = p.id ORDER BY o.id LIMIT 5) AS titles
            FROM property p
            WHERE p.city_id = :cityId
              AND p.property_type = :type
              AND p.transaction_type = :tx
              AND p.area BETWEEN :areaMin AND :areaMax
              AND (ST_DWithin(p.location, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radius)
                   OR (CAST(:districtId AS BIGINT) IS NOT NULL AND p.district_id = :districtId))
              AND NOT EXISTS (SELECT 1 FROM offer o2 WHERE o2.property_id = p.id AND o2.source_id = :sourceId AND o2.active)
            ORDER BY p.location <-> ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography
            LIMIT 25
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PropertyCandidateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PropertyCandidate> findCandidates(NormalizedListing listing, Long sourceId, int radiusMeters) {
        BigDecimal area = listing.area();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("cityId", listing.cityId())
                .addValue("type", listing.propertyType().name())
                .addValue("tx", listing.transactionType().name())
                .addValue("areaMin", area.multiply(new BigDecimal("0.90")))
                .addValue("areaMax", area.multiply(new BigDecimal("1.10")))
                .addValue("lat", listing.lat())
                .addValue("lon", listing.lon())
                .addValue("radius", radiusMeters)
                .addValue("districtId", listing.districtId(), java.sql.Types.BIGINT)
                .addValue("sourceId", sourceId);
        return jdbc.query(SQL, params, (rs, i) -> new PropertyCandidate(
                rs.getLong("id"),
                rs.getLong("city_id"),
                (Long) rs.getObject("district_id"),
                PropertyType.valueOf(rs.getString("property_type")),
                TransactionType.valueOf(rs.getString("transaction_type")),
                rs.getBigDecimal("area"),
                (Integer) rs.getObject("rooms"),
                (Integer) rs.getObject("floor"),
                (Double) rs.getObject("lat"),
                (Double) rs.getObject("lon"),
                rs.getBigDecimal("current_price"),
                toList(rs.getArray("titles"))));
    }

    private static List<String> toList(Array array) throws java.sql.SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((Object[]) array.getArray()).map(String::valueOf).toList();
    }
}
