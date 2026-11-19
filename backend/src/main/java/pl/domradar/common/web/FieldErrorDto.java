package pl.domradar.common.web;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Błąd walidacji pojedynczego pola")
public record FieldErrorDto(
        @Schema(example = "email") String field,
        @Schema(example = "musi być poprawnie sformatowanym adresem e-mail") String message) {
}
