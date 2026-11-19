package pl.domradar.property.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Edycja nieruchomości z optimistic locking: klient odsyła {@code version} otrzymane przy odczycie.
 * Jeśli w międzyczasie ktoś (admin lub import) zmienił nieruchomość, odpowiedź to 409 VERSION_CONFLICT.
 */
@Schema(description = "Edycja atrybutów nieruchomości (z wersją do optimistic locking)")
public record PropertyUpdateRequest(
        @Schema(description = "Wersja z odczytu", example = "3") @NotNull Long version,
        @NotNull @Valid PropertyAttributesRequest attributes) {
}
