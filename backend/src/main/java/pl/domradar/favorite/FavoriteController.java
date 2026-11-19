package pl.domradar.favorite;

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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pl.domradar.auth.CurrentUser;
import pl.domradar.common.web.PageResponse;
import pl.domradar.favorite.dto.FavoriteCreateRequest;
import pl.domradar.favorite.dto.FavoriteDto;
import pl.domradar.favorite.dto.FavoriteNoteRequest;

@Validated
@RestController
@RequestMapping("/api/v1/me/favorites")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Ulubione", description = "Ulubione nieruchomości z notatkami (zalogowany użytkownik)")
public class FavoriteController {

    private final FavoriteService service;

    @GetMapping
    @Operation(summary = "Lista ulubionych (paginacja), z oznaczeniem archiwalnych")
    public PageResponse<FavoriteDto> list(@AuthenticationPrincipal CurrentUser user,
                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(user.id(), PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @PostMapping
    @Operation(summary = "Dodanie do ulubionych")
    @ApiResponse(responseCode = "201", description = "Dodano")
    @ApiResponse(responseCode = "404", description = "Brak nieruchomości")
    @ApiResponse(responseCode = "409", description = "Już w ulubionych (FAVORITE_EXISTS)")
    public ResponseEntity<FavoriteDto> add(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody FavoriteCreateRequest request) {
        FavoriteDto created = service.add(user.id(), request.propertyId(), request.note());
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri()).body(created);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Dodanie / edycja / usunięcie notatki")
    public FavoriteDto updateNote(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                  @Valid @RequestBody FavoriteNoteRequest request) {
        return service.updateNote(user.id(), id, request.note());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Usunięcie z ulubionych")
    @ApiResponse(responseCode = "204", description = "Usunięto")
    public ResponseEntity<Void> remove(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        service.remove(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
