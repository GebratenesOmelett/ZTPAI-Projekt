package pl.domradar.savedsearch;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pl.domradar.auth.CurrentUser;
import pl.domradar.savedsearch.dto.SavedSearchDto;
import pl.domradar.savedsearch.dto.SavedSearchPatchRequest;
import pl.domradar.savedsearch.dto.SavedSearchRequest;

import java.util.List;

@RestController
@RequestMapping("/api/v1/me/saved-searches")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Moje wyszukiwania", description = "Zapisane wyszukiwania i alerty (zalogowany użytkownik)")
public class SavedSearchController {

    private final SavedSearchService service;

    @GetMapping
    @Operation(summary = "Lista zapisanych wyszukiwań")
    public List<SavedSearchDto> list(@AuthenticationPrincipal CurrentUser user) {
        return service.list(user.id());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Szczegóły zapisanego wyszukiwania")
    public SavedSearchDto get(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        return service.get(user.id(), id);
    }

    @PostMapping
    @Operation(summary = "Zapisanie wyszukiwania (opcjonalnie z alertem INSTANT lub DAILY)")
    @ApiResponse(responseCode = "201", description = "Utworzono")
    @ApiResponse(responseCode = "422", description = "Limit 10 wyszukiwań (SAVED_SEARCH_LIMIT) lub 3 alertów INSTANT (INSTANT_ALERT_LIMIT)")
    public ResponseEntity<SavedSearchDto> create(@AuthenticationPrincipal CurrentUser user,
                                                 @Valid @RequestBody SavedSearchRequest request) {
        SavedSearchDto created = service.create(user.id(), request);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri()).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Pełna aktualizacja (nazwa, kryteria, alert)")
    public SavedSearchDto update(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                 @Valid @RequestBody SavedSearchRequest request) {
        return service.update(user.id(), id, request);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Zmiana nazwy lub włączenie/wyłączenie alertu")
    public SavedSearchDto patch(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                @Valid @RequestBody SavedSearchPatchRequest request) {
        return service.patch(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Usunięcie zapisanego wyszukiwania")
    @ApiResponse(responseCode = "204", description = "Usunięto")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id) {
        service.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
