package pl.domradar.property.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Scalenie: wszystkie oferty nieruchomości źródłowej trafiają do docelowej (z URL), źródłowa jest usuwana")
public record MergeRequest(
        @Schema(description = "Nieruchomość scalana (zostanie usunięta)", example = "57") @NotNull Long sourcePropertyId,
        @Schema(description = "Wersja nieruchomości docelowej z odczytu") @NotNull Long targetVersion,
        @Schema(description = "Wersja nieruchomości scalanej z odczytu") @NotNull Long sourceVersion) {
}
