package pl.domradar.search;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;
import pl.domradar.property.MarketType;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;
import pl.domradar.search.validation.ValidSearchCriteria;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kryteria wyszukiwania nieruchomości. Ten sam obiekt jest używany w POST /properties/search,
 * w parametrach GET (linki do udostępnienia) oraz zapisywany w JSONB w {@code saved_search.criteria}.
 * Spójność zakresów, poligonu i promienia sprawdza walidator {@link ValidSearchCriteria}.
 */
@Builder(toBuilder = true)
@ValidSearchCriteria
@Schema(description = "Kryteria wyszukiwania nieruchomości (wszystkie pola opcjonalne)")
public record SearchCriteria(
        @Schema(description = "Zapytanie pełnotekstowe. Słowa poprzedzone '-' są wykluczane.", example = "balkon metro -parter -suterena")
        @Size(max = 200) String q,

        @Schema(description = "Fragment nazwy ulicy (bez polskich znaków też działa)", example = "pulawska")
        @Size(max = 100) String street,

        @Schema(example = "SALE") TransactionType transactionType,
        @Schema(example = "[\"APARTMENT\"]") List<PropertyType> propertyTypes,
        MarketType marketType,

        @Schema(description = "Identyfikatory miast ze słownika", example = "[1]") List<Long> cityIds,
        @Schema(description = "Identyfikatory dzielnic ze słownika", example = "[1, 2]") List<Long> districtIds,

        @PositiveOrZero BigDecimal priceMin,
        @PositiveOrZero BigDecimal priceMax,
        @PositiveOrZero BigDecimal pricePerM2Min,
        @PositiveOrZero BigDecimal pricePerM2Max,
        @PositiveOrZero BigDecimal areaMin,
        @PositiveOrZero BigDecimal areaMax,

        @Schema(description = "Liczby pokoi; 5 oznacza 5 i więcej", example = "[2, 3]")
        List<@Min(1) @Max(10) Integer> rooms,

        @Min(-1) @Max(100) Integer floorMin,
        @Min(-1) @Max(100) Integer floorMax,
        @Schema(description = "Wyklucz parter (piętro 0)") Boolean notGroundFloor,
        @Schema(description = "Wyklucz ostatnie piętro budynku") Boolean notLastFloor,
        @Min(0) @Max(100) Integer totalFloorsMin,
        @Min(0) @Max(100) Integer totalFloorsMax,
        @Min(1800) @Max(2100) Integer yearBuiltMin,
        @Min(1800) @Max(2100) Integer yearBuiltMax,

        @Schema(description = "Kody cech, np. BALCONY, ELEVATOR", example = "[\"BALCONY\", \"ELEVATOR\"]")
        List<@Size(max = 50) String> features,
        @Schema(description = "ALL - wszystkie cechy, ANY - dowolna z cech", example = "ALL") FeatureMode featureMode,

        @Schema(description = "Tylko oferty z tych portali (kody)", example = "[\"otodom\", \"olx\"]") List<String> includeSources,
        @Schema(description = "Pomiń nieruchomości obecne wyłącznie na tych portalach") List<String> excludeSources,
        @Schema(description = "ANY / PRIVATE (tylko prywatne) / AGENCY (tylko biura)") SellerType sellerType,

        @Valid @Schema(description = "Widoczny obszar mapy") BoundingBox bbox,
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @DecimalMin("-180") @DecimalMax("180") Double lon,
        @Schema(description = "Promień w km od punktu (lat, lon), max 50") Double radiusKm,
        @Valid @Schema(description = "Poligon GeoJSON narysowany na mapie (max 100 punktów)") GeoPolygon polygon,

        @Min(1) @Max(3650) Integer addedWithinDays,
        @Min(1) @Max(3650) Integer priceDropWithinDays,
        @DecimalMin("0") @DecimalMax("100") BigDecimal minPriceDropPercent,
        @Min(0) Integer daysOnMarketMin,
        @Min(0) Integer daysOnMarketMax,

        @Schema(description = "Tylko nieruchomości obecne na co najmniej 2 portalach") Boolean multiSource,
        @Schema(description = "Pokaż również archiwalne") Boolean includeArchived
) {

    public static SearchCriteria empty() {
        return SearchCriteria.builder().build();
    }

    public FeatureMode effectiveFeatureMode() {
        return featureMode == null ? FeatureMode.ALL : featureMode;
    }

    public SellerType effectiveSellerType() {
        return sellerType == null ? SellerType.ANY : sellerType;
    }

    public boolean hasPoint() {
        return lat != null && lon != null;
    }
}
