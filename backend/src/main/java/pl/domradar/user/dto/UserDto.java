package pl.domradar.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.domradar.user.Role;

import java.time.Instant;

@Schema(description = "Użytkownik")
public record UserDto(
        @Schema(example = "1") Long id,
        @Schema(example = "jan@example.com") String email,
        @Schema(example = "Jan Kowalski") String displayName,
        @Schema(example = "USER") Role role,
        boolean enabled,
        Instant createdAt) {
}
