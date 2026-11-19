package pl.domradar.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.domradar.property.MarketType;
import pl.domradar.property.PropertyStatus;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Nieruchomość na liście wyników")
public record PropertySummaryDto(
        @Schema(example = "123") Long id,
        @Schema(example = "Słoneczne 3-pokojowe mieszkanie 62,5 m², Mokotów") String title,
        PropertyType propertyType,
        TransactionType transactionType,
        MarketType marketType,
        Long cityId,
        @Schema(example = "Warszawa") String city,
        Long districtId,
        @Schema(example = "Mokotów") String district,
        @Schema(example = "Puławska") String street,
        Double lat,
        Double lon,
        @Schema(example = "62.50") BigDecimal area,
        Integer rooms,
        Integer floor,
        Integer totalFloors,
        Integer yearBuilt,
        @Schema(example = "1199000.00") BigDecimal price,
        @Schema(example = "19184.00") BigDecimal pricePerM2,
        boolean priceDropped,
        @Schema(description = "Obniżka względem najwyższej ceny w historii (%)", example = "4.50") BigDecimal priceDropPercent,
        Instant lastPriceChangeAt,
        @Schema(description = "Na ilu portalach jest aktywna oferta", example = "2") int sourceCount,
        Instant firstSeenAt,
        @Schema(example = "12") long daysOnMarket,
        PropertyStatus status,
        String mainPhotoUrl,
        @Schema(description = "Mediana ceny za m² w dzielnicy (ten sam typ i transakcja)", example = "19500.00") BigDecimal districtMedianPricePerM2,
        @Schema(description = "Różnica ceny za m² względem mediany dzielnicy w %, ujemna = taniej", example = "-1.6") BigDecimal vsMedianPercent,
        @Schema(description = "Odległość od punktu wyszukiwania w metrach (gdy podano lat/lon)") Double distanceMeters) {
}
