package pl.domradar.property.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import pl.domradar.property.MarketType;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Atrybuty nieruchomości")
public record PropertyAttributesRequest(
        @Schema(example = "APARTMENT") @NotNull PropertyType propertyType,
        @Schema(example = "SALE") @NotNull TransactionType transactionType,
        @Schema(example = "SECONDARY") MarketType marketType,
        @Schema(example = "1") @NotNull Long cityId,
        @Schema(example = "1") Long districtId,
        @Schema(example = "Puławska 12") @Size(max = 200) String street,
        @Schema(description = "Brak współrzędnych = geokodowanie po dzielnicy i ulicy", example = "52.1934")
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @Schema(example = "21.0346") @DecimalMin("-180") @DecimalMax("180") Double lon,
        @Schema(example = "62.5") @NotNull @DecimalMin("5") @DecimalMax("100000") BigDecimal area,
        @Schema(example = "3") @Min(1) @Max(20) Integer rooms,
        @Schema(example = "4") @Min(-1) @Max(100) Integer floor,
        @Schema(example = "10") @Min(0) @Max(100) Integer totalFloors,
        @Schema(example = "2008") @Min(1800) @Max(2100) Integer yearBuilt,
        @Schema(example = "[\"BALCONY\", \"ELEVATOR\"]") List<@Size(max = 50) String> features) {
}
