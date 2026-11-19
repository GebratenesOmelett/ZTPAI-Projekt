package pl.domradar.search.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import pl.domradar.search.BoundingBox;
import pl.domradar.search.GeoPolygon;
import pl.domradar.search.SearchCriteria;

import java.util.ArrayList;
import java.util.List;

/**
 * Walidator kryteriów wyszukiwania. Logika jest dostępna również statycznie
 * ({@link #findViolations(SearchCriteria)}), dzięki czemu można ją testować jednostkowo
 * i wykorzystać przy walidacji parametrów GET.
 */
public class SearchCriteriaValidator implements ConstraintValidator<ValidSearchCriteria, SearchCriteria> {

    public static final int MAX_POLYGON_POINTS = 100;
    public static final double MAX_RADIUS_KM = 50.0;

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    public record Violation(String field, String message) {
    }

    @Override
    public boolean isValid(SearchCriteria criteria, ConstraintValidatorContext context) {
        if (criteria == null) {
            return true;
        }
        List<Violation> violations = findViolations(criteria);
        if (violations.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        for (Violation v : violations) {
            context.buildConstraintViolationWithTemplate(v.message())
                    .addPropertyNode(v.field())
                    .addConstraintViolation();
        }
        return false;
    }

    public static List<Violation> findViolations(SearchCriteria c) {
        List<Violation> out = new ArrayList<>();
        range(out, "price", c.priceMin(), c.priceMax());
        range(out, "pricePerM2", c.pricePerM2Min(), c.pricePerM2Max());
        range(out, "area", c.areaMin(), c.areaMax());
        range(out, "floor", c.floorMin(), c.floorMax());
        range(out, "totalFloors", c.totalFloorsMin(), c.totalFloorsMax());
        range(out, "yearBuilt", c.yearBuiltMin(), c.yearBuiltMax());
        range(out, "daysOnMarket", c.daysOnMarketMin(), c.daysOnMarketMax());

        validatePointAndRadius(out, c);
        validateBbox(out, c.bbox());
        validatePolygon(out, c.polygon());
        return out;
    }

    private static <T extends Comparable<T>> void range(List<Violation> out, String name, T min, T max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            out.add(new Violation(name + "Min", "Wartość minimalna nie może być większa od maksymalnej (" + name + ")"));
        }
    }

    private static void validatePointAndRadius(List<Violation> out, SearchCriteria c) {
        boolean hasLat = c.lat() != null;
        boolean hasLon = c.lon() != null;
        if (hasLat != hasLon) {
            out.add(new Violation(hasLat ? "lon" : "lat", "Punkt wymaga podania obu współrzędnych: lat i lon"));
        }
        if (c.radiusKm() != null) {
            if (!(hasLat && hasLon)) {
                out.add(new Violation("radiusKm", "Promień wymaga podania punktu (lat, lon)"));
            }
            if (c.radiusKm() <= 0) {
                out.add(new Violation("radiusKm", "Promień musi być dodatni"));
            } else if (c.radiusKm() > MAX_RADIUS_KM) {
                out.add(new Violation("radiusKm", "Promień nie może przekraczać " + (int) MAX_RADIUS_KM + " km"));
            }
        }
    }

    private static void validateBbox(List<Violation> out, BoundingBox b) {
        if (b == null || b.south() == null || b.north() == null || b.west() == null || b.east() == null) {
            return;
        }
        if (b.south() > b.north()) {
            out.add(new Violation("bbox", "Południowa granica obszaru nie może być większa od północnej"));
        }
        if (b.west() > b.east()) {
            out.add(new Violation("bbox", "Zachodnia granica obszaru nie może być większa od wschodniej"));
        }
    }

    private static void validatePolygon(List<Violation> out, GeoPolygon polygon) {
        if (polygon == null) {
            return;
        }
        if (!"Polygon".equals(polygon.type())) {
            out.add(new Violation("polygon", "Obsługiwany jest wyłącznie typ GeoJSON 'Polygon'"));
            return;
        }
        List<List<Double>> ring = polygon.outerRing();
        for (List<Double> point : ring) {
            if (point == null || point.size() < 2 || point.get(0) == null || point.get(1) == null
                    || Math.abs(point.get(0)) > 180 || Math.abs(point.get(1)) > 90) {
                out.add(new Violation("polygon", "Każdy punkt poligonu musi mieć poprawne współrzędne [lon, lat]"));
                return;
            }
        }
        List<Coordinate> coords = toClosedRing(ring);
        int distinctPoints = coords.size() - 1;
        if (distinctPoints < 3) {
            out.add(new Violation("polygon", "Poligon musi mieć co najmniej 3 punkty"));
            return;
        }
        if (distinctPoints > MAX_POLYGON_POINTS) {
            out.add(new Violation("polygon", "Poligon może mieć maksymalnie " + MAX_POLYGON_POINTS + " punktów"));
            return;
        }
        Polygon jts = GEOMETRY_FACTORY.createPolygon(coords.toArray(Coordinate[]::new));
        if (!jts.isValid() || jts.getArea() == 0) {
            out.add(new Violation("polygon", "Poligon jest niepoprawny (samoprzecięcia lub zerowa powierzchnia)"));
        }
    }

    /** Zamienia pierścień GeoJSON na listę współrzędnych JTS, domykając go w razie potrzeby. */
    public static List<Coordinate> toClosedRing(List<List<Double>> ring) {
        List<Coordinate> coords = new ArrayList<>();
        for (List<Double> p : ring) {
            coords.add(new Coordinate(p.get(0), p.get(1)));
        }
        if (!coords.isEmpty() && !coords.getFirst().equals2D(coords.getLast())) {
            coords.add(new Coordinate(coords.getFirst()));
        }
        if (coords.size() == 1) {
            coords.add(new Coordinate(coords.getFirst()));
        }
        return coords;
    }
}
