package pl.domradar.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Dane rejestracji nowego konta")
public record RegisterRequest(
        @Schema(example = "jan@example.com")
        @NotBlank @Email @Size(max = 255) String email,

        @Schema(example = "Haslo12345", description = "8-72 znaki, co najmniej jedna litera i jedna cyfra")
        @NotBlank @Size(min = 8, max = 72)
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\d).+$", message = "musi zawierać co najmniej jedną literę i jedną cyfrę")
        String password,

        @Schema(example = "Jan Kowalski")
        @NotBlank @Size(min = 2, max = 100) String displayName) {
}
