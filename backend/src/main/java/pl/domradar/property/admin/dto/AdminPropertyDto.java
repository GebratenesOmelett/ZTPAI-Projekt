package pl.domradar.property.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.domradar.property.PropertyStatus;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Nieruchomość na liście w panelu administratora")
public record AdminPropertyDto(
        Long id,
        String title,
        PropertyType propertyType,
        TransactionType transactionType,
        String city,
        String district,
        String street,
        BigDecimal area,
        Integer rooms,
        Integer floor,
        BigDecimal price,
        BigDecimal pricePerM2,
        PropertyStatus status,
        int sourceCount,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Long version) {
}
