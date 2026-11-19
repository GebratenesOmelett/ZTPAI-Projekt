package pl.domradar.search;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Geometria GeoJSON typu Polygon. Współrzędne w kolejności [lon, lat]; brany jest pod uwagę
 * wyłącznie pierwszy (zewnętrzny) pierścień.
 */
@Schema(description = "GeoJSON Polygon, współrzędne [lon, lat]",
        example = "{\"type\":\"Polygon\",\"coordinates\":[[[21.0,52.2],[21.05,52.2],[21.05,52.23],[21.0,52.23],[21.0,52.2]]]}")
public record GeoPolygon(String type, List<List<List<Double>>> coordinates) {

    public List<List<Double>> outerRing() {
        if (coordinates == null || coordinates.isEmpty()) {
            return List.of();
        }
        return coordinates.getFirst();
    }
}
