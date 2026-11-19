package pl.domradar.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.domradar.user.Role;

@Schema(description = "Zmiana roli i/lub blokada konta (pola null są pomijane)")
public record UserUpdateRequest(
        @Schema(example = "ADMIN") Role role,
        @Schema(description = "false = konto zablokowane", example = "false") Boolean enabled) {
}
