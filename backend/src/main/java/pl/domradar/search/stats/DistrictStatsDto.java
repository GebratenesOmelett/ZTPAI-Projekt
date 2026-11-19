package pl.domradar.search.stats;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Statystyki cen za m² w dzielnicy")
public record DistrictStatsDto(
        Long districtId,
        @Schema(example = "Mokotów") String district,
        Long cityId,
        @Schema(example = "Warszawa") String city,
        PropertyType propertyType,
        TransactionType transactionType,
        @Schema(example = "19450.00") BigDecimal medianPricePerM2,
        @Schema(example = "19820.35") BigDecimal avgPricePerM2,
        @Schema(example = "87") int listingCount,
        Instant updatedAt) {
}
