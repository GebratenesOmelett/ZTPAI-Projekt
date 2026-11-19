package pl.domradar.property.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(description = "Oferta z konkretnego portalu")
public record OfferDto(
        Long id,
        Long propertyId,
        Long sourceId,
        @Schema(example = "otodom") String sourceCode,
        @Schema(example = "Otodom") String sourceName,
        String externalId,
        String url,
        String title,
        BigDecimal price,
        boolean active,
        boolean privateSeller,
        String agencyName,
        Instant firstSeenAt,
        Instant lastSeenAt,
        int missedImports,
        List<PricePointDto> priceHistory) {

    @Schema(description = "Punkt historii ceny")
    public record PricePointDto(BigDecimal price, Instant changedAt) {
    }
}
