package pl.domradar.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.domradar.user.dto.UserDto;

@Schema(description = "Odpowiedź po zalogowaniu / odświeżeniu. Refresh token jest ustawiany w ciasteczku httpOnly.")
public record AuthResponse(
        @Schema(description = "Access token JWT (ważny krótko, przechowywany w pamięci klienta)") String accessToken,
        @Schema(example = "Bearer") String tokenType,
        @Schema(description = "Czas ważności access tokenu w sekundach", example = "900") long expiresIn,
        UserDto user) {
}
