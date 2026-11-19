package pl.domradar.search.query;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import pl.domradar.common.util.TextNormalizer;
import pl.domradar.search.BoundingBox;
import pl.domradar.search.SearchCriteria;
import pl.domradar.search.SellerType;
import pl.domradar.search.validation.SearchCriteriaValidator;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Buduje klauzulę WHERE wyszukiwarki z kryteriów. Każdy warunek ma przypisany wymiar fasety —
 * fasety liczymy "rozłącznie" (z pominięciem filtra własnego wymiaru), dzięki czemu użytkownik widzi
 * liczebności alternatyw, np. ile wyników dałaby inna dzielnica. Wszystkie wartości trafiają do SQL
 * jako parametry nazwane (brak SQL injection); nazwy kolumn sortowania pochodzą z białej listy.
 */
public final class SearchSqlBuilder {

    public enum Dimension {
        NONE, TYPE, DISTRICT, ROOMS, PRICE, SOURCE
    }

    private record Condition(Dimension dimension, String sql) {
    }

    private final List<Condition> conditions = new ArrayList<>();
    private final MapSqlParameterSource params = new MapSqlParameterSource();
    private final TextQuery text;
    private final boolean hasPoint;
    private int counter;

    private SearchSqlBuilder(SearchCriteria c) {
        this.text = TextQuery.parse(c.q());
        this.hasPoint = c.hasPoint();
        build(c);
    }

    public static SearchSqlBuilder from(SearchCriteria criteria) {
        return new SearchSqlBuilder(criteria == null ? SearchCriteria.empty() : criteria);
    }

    public String where() {
        return where(Dimension.NONE);
    }

    /** WHERE z pominięciem warunków danego wymiaru (do faset). */
    public String where(Dimension excluded) {
        String sql = conditions.stream()
                .filter(cond -> excluded == Dimension.NONE || cond.dimension() != excluded)
                .map(Condition::sql)
                .collect(Collectors.joining("\n  AND "));
        return sql.isEmpty() ? "TRUE" : sql;
    }

    public MapSqlParameterSource params() {
        return params;
    }

    public boolean hasRelevance() {
        return text.hasPositiveTerms();
    }

    public boolean hasDistance() {
        return hasPoint;
    }

    public String relevanceExpression() {
        if (!text.hasPositiveTerms()) {
            return "0.0";
        }
        params.addValue("rankQuery", text.rankQuery());
        params.addValue("rankText", text.positiveText());
        return "(ts_rank(p.search_vector, to_tsquery('domradar_pl', :rankQuery)) + 0.3 * word_similarity(:rankText, p.search_text))";
    }

    public String distanceExpression() {
        return hasPoint ? "ST_Distance(p.location, ST_SetSRID(ST_MakePoint(:pointLon, :pointLat), 4326)::geography)" : "NULL";
    }

    private String param(String prefix, Object value) {
        String name = prefix + (counter++);
        params.addValue(name, value);
        return ":" + name;
    }

    private void add(String sql) {
        conditions.add(new Condition(Dimension.NONE, sql));
    }

    private void add(Dimension dimension, String sql) {
        conditions.add(new Condition(dimension, sql));
    }

    private void build(SearchCriteria c) {
        if (!Boolean.TRUE.equals(c.includeArchived())) {
            add("p.status = 'ACTIVE'");
        }
        buildText(c);
        if (notBlank(c.street())) {
            String folded = TextNormalizer.fold(c.street()).replaceAll("^(ul\\.?|ulica)\\s+", "");
            add("(f_unaccent(lower(p.street)) LIKE " + param("streetLike", "%" + escapeLike(folded) + "%")
                    + " OR f_unaccent(lower(p.street)) % " + param("street", folded) + ")");
        }
        if (c.transactionType() != null) {
            add("p.transaction_type = " + param("tx", c.transactionType().name()));
        }
        if (notEmpty(c.propertyTypes())) {
            add(Dimension.TYPE, "p.property_type IN (" + param("types", c.propertyTypes().stream().map(Enum::name).toList()) + ")");
        }
        if (c.marketType() != null) {
            add("p.market_type = " + param("market", c.marketType().name()));
        }
        if (notEmpty(c.cityIds())) {
            add("p.city_id IN (" + param("cities", c.cityIds()) + ")");
        }
        if (notEmpty(c.districtIds())) {
            add(Dimension.DISTRICT, "p.district_id IN (" + param("districts", c.districtIds()) + ")");
        }
        range(Dimension.PRICE, "p.current_price", c.priceMin(), c.priceMax());
        range(Dimension.NONE, "p.price_per_m2", c.pricePerM2Min(), c.pricePerM2Max());
        range(Dimension.NONE, "p.area", c.areaMin(), c.areaMax());
        buildRooms(c.rooms());
        range(Dimension.NONE, "p.floor", c.floorMin(), c.floorMax());
        if (Boolean.TRUE.equals(c.notGroundFloor())) {
            add("p.floor IS NOT NULL AND p.floor > 0");
        }
        if (Boolean.TRUE.equals(c.notLastFloor())) {
            add("p.floor IS NOT NULL AND p.total_floors IS NOT NULL AND p.floor < p.total_floors");
        }
        range(Dimension.NONE, "p.total_floors", c.totalFloorsMin(), c.totalFloorsMax());
        range(Dimension.NONE, "p.year_built", c.yearBuiltMin(), c.yearBuiltMax());
        buildFeatures(c);
        buildSources(c);
        buildSeller(c.effectiveSellerType());
        buildGeo(c);
        if (c.addedWithinDays() != null) {
            add("p.first_seen_at >= now() - make_interval(days => " + param("addedDays", c.addedWithinDays()) + ")");
        }
        if (c.priceDropWithinDays() != null) {
            add("p.last_price_drop_at >= now() - make_interval(days => " + param("dropDays", c.priceDropWithinDays()) + ")");
        }
        if (c.minPriceDropPercent() != null && c.minPriceDropPercent().signum() > 0) {
            add("p.price_drop_percent >= " + param("minDrop", c.minPriceDropPercent()));
        }
        if (c.daysOnMarketMin() != null) {
            add("p.first_seen_at <= now() - make_interval(days => " + param("domMin", c.daysOnMarketMin()) + ")");
        }
        if (c.daysOnMarketMax() != null) {
            add("p.first_seen_at >= now() - make_interval(days => " + param("domMax", c.daysOnMarketMax()) + ")");
        }
        if (Boolean.TRUE.equals(c.multiSource())) {
            add("p.active_source_count >= 2");
        }
    }

    private void buildText(SearchCriteria c) {
        for (String term : text.include()) {
            String tsq = param("q", TextQuery.prefixQuery(term));
            if (term.length() >= 4) {
                add("(p.search_vector @@ to_tsquery('domradar_pl', " + tsq + ") OR "
                        + param("qf", term) + " <% p.search_text)");
            } else {
                add("p.search_vector @@ to_tsquery('domradar_pl', " + tsq + ")");
            }
        }
        for (String term : text.exclude()) {
            add("NOT (p.search_vector @@ to_tsquery('domradar_pl', " + param("nq", TextQuery.prefixQuery(term)) + "))");
        }
    }

    private void buildRooms(List<Integer> rooms) {
        if (!notEmpty(rooms)) {
            return;
        }
        List<Integer> exact = rooms.stream().filter(Objects::nonNull).filter(r -> r < 5).distinct().toList();
        boolean fivePlus = rooms.stream().anyMatch(r -> r != null && r >= 5);
        List<String> parts = new ArrayList<>();
        if (!exact.isEmpty()) {
            parts.add("p.rooms IN (" + param("rooms", exact) + ")");
        }
        if (fivePlus) {
            parts.add("p.rooms >= 5");
        }
        add(Dimension.ROOMS, "(" + String.join(" OR ", parts) + ")");
    }

    private void buildFeatures(SearchCriteria c) {
        if (!notEmpty(c.features())) {
            return;
        }
        List<String> codes = c.features().stream().filter(Objects::nonNull).map(s -> s.trim().toUpperCase(Locale.ROOT)).distinct().toList();
        String codesParam = param("features", codes);
        String sub = "SELECT 1 FROM property_feature pf JOIN feature f ON f.id = pf.feature_id WHERE pf.property_id = p.id AND f.code IN (" + codesParam + ")";
        if (c.effectiveFeatureMode() == pl.domradar.search.FeatureMode.ANY) {
            add("EXISTS (" + sub + ")");
        } else {
            add("(SELECT count(DISTINCT f.code) FROM property_feature pf JOIN feature f ON f.id = pf.feature_id "
                    + "WHERE pf.property_id = p.id AND f.code IN (" + codesParam + ")) = " + param("featureCount", codes.size()));
        }
    }

    private void buildSources(SearchCriteria c) {
        String offerVisible = "(o.active OR p.status = 'ARCHIVED')";
        if (notEmpty(c.includeSources())) {
            add(Dimension.SOURCE, "EXISTS (SELECT 1 FROM offer o JOIN source so ON so.id = o.source_id WHERE o.property_id = p.id AND "
                    + offerVisible + " AND so.code IN (" + param("incSources", c.includeSources()) + "))");
        }
        if (notEmpty(c.excludeSources())) {
            add(Dimension.SOURCE, "EXISTS (SELECT 1 FROM offer o JOIN source so ON so.id = o.source_id WHERE o.property_id = p.id AND "
                    + offerVisible + " AND so.code NOT IN (" + param("excSources", c.excludeSources()) + "))");
        }
    }

    private void buildSeller(SellerType sellerType) {
        if (sellerType == SellerType.PRIVATE) {
            add("EXISTS (SELECT 1 FROM offer o WHERE o.property_id = p.id AND o.active AND o.private_seller)");
        } else if (sellerType == SellerType.AGENCY) {
            add("EXISTS (SELECT 1 FROM offer o WHERE o.property_id = p.id AND o.active AND NOT o.private_seller)");
        }
    }

    private void buildGeo(SearchCriteria c) {
        BoundingBox b = c.bbox();
        if (b != null && b.south() != null && b.west() != null && b.north() != null && b.east() != null) {
            add("ST_Intersects(p.location, ST_MakeEnvelope(" + param("w", b.west()) + ", " + param("s", b.south()) + ", "
                    + param("e", b.east()) + ", " + param("n", b.north()) + ", 4326)::geography)");
        }
        if (c.hasPoint()) {
            params.addValue("pointLat", c.lat());
            params.addValue("pointLon", c.lon());
            if (c.radiusKm() != null) {
                add("ST_DWithin(p.location, ST_SetSRID(ST_MakePoint(:pointLon, :pointLat), 4326)::geography, "
                        + param("radius", c.radiusKm() * 1000) + ")");
            }
        }
        if (c.polygon() != null && !c.polygon().outerRing().isEmpty()) {
            String wkt = SearchCriteriaValidator.toClosedRing(c.polygon().outerRing()).stream()
                    .map(coord -> String.format(Locale.ROOT, "%.7f %.7f", coord.x, coord.y))
                    .collect(Collectors.joining(", ", "POLYGON((", "))"));
            add("ST_Intersects(p.location, ST_GeogFromText(" + param("polygon", "SRID=4326;" + wkt) + "))");
        }
    }

    private <T> void range(Dimension dimension, String column, T min, T max) {
        if (min != null) {
            add(dimension, column + " >= " + param("min", min));
        }
        if (max != null) {
            add(dimension, column + " <= " + param("max", max));
        }
    }

    private static boolean notEmpty(Collection<?> c) {
        return c != null && !c.isEmpty();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
