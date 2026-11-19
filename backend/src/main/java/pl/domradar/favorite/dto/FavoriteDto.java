package pl.domradar.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.domradar.property.PropertyStatus;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Ulubiona nieruchomość z notatką")
public record FavoriteDto(
        Long id,
        Long propertyId,
        @Schema(example = "Moje typy: blisko pracy") String note,
        Instant createdAt,
        String title,
        PropertyType propertyType,
        TransactionType transactionType,
        String city,
        String district,
        BigDecimal area,
        Integer rooms,
        BigDecimal price,
        BigDecimal pricePerM2,
        boolean priceDropped,
        String mainPhotoUrl,
        @Schema(description = "ARCHIVED = brak aktywnych ofert na portalach") PropertyStatus status,
        boolean archived) {
}
