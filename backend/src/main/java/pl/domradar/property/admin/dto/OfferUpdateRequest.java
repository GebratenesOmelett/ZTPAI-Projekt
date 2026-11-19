package pl.domradar.property.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;

@Schema(description = "Edycja oferty (zmiana ceny tworzy wpis w historii cen)")
public record OfferUpdateRequest(
        @NotBlank @Size(max = 300) String title,
        @Size(max = 10000) String description,
        @URL @Size(max = 1000) String url,
        @NotNull @Positive BigDecimal price,
        @NotNull Boolean active,
        boolean privateSeller,
        @Size(max = 200) String agencyName) {
}
