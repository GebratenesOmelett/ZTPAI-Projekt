package pl.domradar.property;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/** Pomocnicze funkcje tworzenia nieruchomości: punkt geograficzny (SRID 4326). */
public final class PropertyFactory {

    private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

    private PropertyFactory() {
    }

    public static Point point(double lat, double lon) {
        return GEOMETRY.createPoint(new Coordinate(lon, lat));
    }
}
