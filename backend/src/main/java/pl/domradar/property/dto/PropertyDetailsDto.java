package pl.domradar.property.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.domradar.property.MarketType;
import pl.domradar.property.PropertyStatus;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;
import pl.domradar.property.dictionary.dto.FeatureDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Szczegóły nieruchomości: wszystkie oferty z portali, historia cen, zdjęcia, porównanie do mediany")
public record PropertyDetailsDto(
        Long id,
        String title,
        String description,
        PropertyType propertyType,
        TransactionType transactionType,
        MarketType marketType,
        Long cityId,
        String city,
        Long districtId,
        String district,
        String street,
        Double lat,
        Double lon,
        BigDecimal area,
        Integer rooms,
        Integer floor,
        Integer totalFloors,
        Integer yearBuilt,
        BigDecimal price,
        BigDecimal pricePerM2,
        boolean priceDropped,
        BigDecimal priceDropPercent,
        Instant lastPriceChangeAt,
        Instant lastPriceDropAt,
        int sourceCount,
        Instant firstSeenAt,
        Instant lastSeenAt,
        long daysOnMarket,
        PropertyStatus status,
        List<FeatureDto> features,
        List<String> photos,
        List<OfferDto> offers,
        MedianComparisonDto median,
        @Schema(description = "Wersja do optimistic locking (edycja przez admina)") Long version,
        @Schema(description = "Id ulubionego, jeśli zalogowany użytkownik dodał nieruchomość do ulubionych") Long favoriteId) {

    @Schema(description = "Porównanie ceny za m² do mediany dzielnicy")
    public record MedianComparisonDto(
            @Schema(example = "19500.00") BigDecimal districtMedianPricePerM2,
            @Schema(description = "ujemne = taniej niż mediana", example = "-8.4") BigDecimal vsMedianPercent,
            @Schema(example = "57") int sampleSize,
            @Schema(example = "8,4% poniżej mediany dzielnicy") String label) {
    }
}
