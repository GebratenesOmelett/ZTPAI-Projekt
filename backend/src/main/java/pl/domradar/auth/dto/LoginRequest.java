package pl.domradar.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Dane logowania")
public record LoginRequest(
        @Schema(example = "jan@example.com") @NotBlank String email,
        @Schema(example = "Haslo12345") @NotBlank String password) {
}
