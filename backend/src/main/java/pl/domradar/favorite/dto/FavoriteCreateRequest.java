package pl.domradar.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Dodanie nieruchomości do ulubionych")
public record FavoriteCreateRequest(
        @Schema(example = "42") @NotNull @Positive Long propertyId,
        @Schema(example = "Zadzwonić w poniedziałek") @Size(max = 2000) String note) {
}
