package pl.domradar.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.property.MarketType;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Płaska wersja kryteriów do wyszukiwania przez GET (linki do udostępnienia).
 * Listy jako powtórzone parametry lub wartości po przecinku, {@code bbox=south,west,north,east},
 * {@code polygon} jako GeoJSON zakodowany w URL.
 */
@Schema(description = "Parametry wyszukiwania GET (te same znaczenia co w SearchCriteria)")
public record SearchParams(
        String q, String street, TransactionType transactionType, List<PropertyType> propertyTypes, MarketType marketType,
        List<Long> cityIds, List<Long> districtIds,
        BigDecimal priceMin, BigDecimal priceMax, BigDecimal pricePerM2Min, BigDecimal pricePerM2Max,
        BigDecimal areaMin, BigDecimal areaMax, List<Integer> rooms,
        Integer floorMin, Integer floorMax, Boolean notGroundFloor, Boolean notLastFloor,
        Integer totalFloorsMin, Integer totalFloorsMax, Integer yearBuiltMin, Integer yearBuiltMax,
        List<String> features, FeatureMode featureMode, List<String> includeSources, List<String> excludeSources,
        SellerType sellerType,
        @Schema(description = "south,west,north,east", example = "52.15,20.90,52.30,21.15") String bbox,
        Double lat, Double lon, Double radiusKm,
        @Schema(description = "GeoJSON Polygon") String polygon,
        Integer addedWithinDays, Integer priceDropWithinDays, BigDecimal minPriceDropPercent,
        Integer daysOnMarketMin, Integer daysOnMarketMax, Boolean multiSource, Boolean includeArchived) {

    public SearchCriteria toCriteria(ObjectMapper objectMapper) {
        return new SearchCriteria(q, street, transactionType, propertyTypes, marketType, cityIds, districtIds,
                priceMin, priceMax, pricePerM2Min, pricePerM2Max, areaMin, areaMax, rooms,
                floorMin, floorMax, notGroundFloor, notLastFloor, totalFloorsMin, totalFloorsMax, yearBuiltMin, yearBuiltMax,
                features, featureMode, includeSources, excludeSources, sellerType,
                parseBbox(bbox), lat, lon, radiusKm, parsePolygon(polygon, objectMapper),
                addedWithinDays, priceDropWithinDays, minPriceDropPercent, daysOnMarketMin, daysOnMarketMax,
                multiSource, includeArchived);
    }

    static BoundingBox parseBbox(String bbox) {
        if (bbox == null || bbox.isBlank()) {
            return null;
        }
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw invalid("bbox", "Oczekiwano 4 liczb: south,west,north,east");
        }
        try {
            return new BoundingBox(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim()));
        } catch (NumberFormatException e) {
            throw invalid("bbox", "Niepoprawne współrzędne obszaru");
        }
    }

    static GeoPolygon parsePolygon(String polygon, ObjectMapper objectMapper) {
        if (polygon == null || polygon.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(polygon, GeoPolygon.class);
        } catch (Exception e) {
            throw invalid("polygon", "Niepoprawny GeoJSON poligonu");
        }
    }

    private static DomainException invalid(String field, String message) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, message, Map.of("field", field));
    }
}
