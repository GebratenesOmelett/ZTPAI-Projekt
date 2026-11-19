package pl.domradar.property.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "Rozdzielenie: wskazane oferty przechodzą do nowej nieruchomości (utworzonej z atrybutów pierwszej z nich)")
public record SplitRequest(
        @Schema(example = "[101, 102]") @NotEmpty List<@NotNull Long> offerIds,
        @Schema(description = "Wersja nieruchomości z odczytu") @NotNull Long version) {
}
