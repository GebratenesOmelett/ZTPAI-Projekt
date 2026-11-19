package pl.domradar.property.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;
import java.util.List;

/**
 * Ręczne dodanie oferty. Z {@code propertyId} — oferta trafia do wskazanej nieruchomości;
 * bez niego — wymagane są {@code attributes}, a oferta przechodzi przez deduplikację
 * (dołączenie do istniejącej nieruchomości albo utworzenie nowej).
 */
@Schema(description = "Ręcznie dodawana oferta")
public record OfferCreateRequest(
        @Schema(description = "Źródło (domyślnie 'manual' — Dodane ręcznie)", example = "7") Long sourceId,
        @Schema(description = "Id ogłoszenia w portalu; puste = wygenerowane", example = "MAN-001") @Size(max = 100) String externalId,
        @Schema(description = "Nieruchomość, do której dołączyć ofertę") Long propertyId,
        @Schema(example = "https://www.example.com/oferta/1") @URL @Size(max = 1000) String url,
        @Schema(example = "Mieszkanie 3 pokoje, Mokotów") @NotBlank @Size(max = 300) String title,
        @Size(max = 10000) String description,
        @Schema(example = "1150000") @NotNull @Positive BigDecimal price,
        boolean privateSeller,
        @Size(max = 200) String agencyName,
        List<@URL @Size(max = 1000) String> photos,
        @Valid PropertyAttributesRequest attributes) {
}
