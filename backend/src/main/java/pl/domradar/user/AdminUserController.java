package pl.domradar.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pl.domradar.auth.CurrentUser;
import pl.domradar.common.web.PageResponse;
import pl.domradar.user.dto.UserDto;
import pl.domradar.user.dto.UserUpdateRequest;

@Validated
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin: użytkownicy", description = "Zarządzanie rolami i blokadą kont (rola ADMIN)")
public class AdminUserController {

    private final UserAdminService service;

    @GetMapping
    @Operation(summary = "Lista użytkowników (wyszukiwanie po e-mailu/nazwie, filtr roli, paginacja)")
    public PageResponse<UserDto> list(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) Role role,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(q, role, PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Szczegóły użytkownika")
    public UserDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Zmiana roli lub blokada konta")
    @ApiResponse(responseCode = "409", description = "Próba zdegradowania/zablokowania siebie (SELF_MODIFICATION) lub ostatniego admina (LAST_ADMIN)")
    public UserDto update(@AuthenticationPrincipal CurrentUser actor, @PathVariable Long id,
                          @Valid @RequestBody UserUpdateRequest request) {
        return service.update(actor.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Usunięcie konta (wraz z wyszukiwaniami, ulubionymi i powiadomieniami)")
    @ApiResponse(responseCode = "204", description = "Usunięto")
    @ApiResponse(responseCode = "409", description = "Usuwanie siebie lub ostatniego admina")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CurrentUser actor, @PathVariable Long id) {
        service.delete(actor.id(), id);
        return ResponseEntity.noContent().build();
    }
}
