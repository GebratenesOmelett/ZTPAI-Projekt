package pl.domradar.property.admin;

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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pl.domradar.common.web.PageResponse;
import pl.domradar.property.admin.dto.OfferCreateRequest;
import pl.domradar.property.admin.dto.OfferUpdateRequest;
import pl.domradar.property.dto.OfferDto;

@Validated
@RestController
@RequestMapping("/api/v1/admin/offers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin: oferty", description = "Przegląd, ręczne dodawanie i edycja ofert (rola ADMIN)")
public class AdminOfferController {

    private final OfferAdminService service;

    @GetMapping
    @Operation(summary = "Lista ofert (filtry: źródło, nieruchomość, aktywność; paginacja)")
    public PageResponse<OfferDto> list(@RequestParam(required = false) Long sourceId,
                                       @RequestParam(required = false) Long propertyId,
                                       @RequestParam(required = false) Boolean active,
                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                       @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(sourceId, propertyId, active, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastSeenAt")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Szczegóły oferty")
    public OfferDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(summary = "Ręczne dodanie oferty (do wskazanej nieruchomości lub przez deduplikację)")
    @ApiResponse(responseCode = "201", description = "Dodano")
    @ApiResponse(responseCode = "409", description = "Oferta o tym externalId już istnieje w źródle")
    public ResponseEntity<OfferDto> create(@Valid @RequestBody OfferCreateRequest request) {
        OfferDto created = service.create(request, false);
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(created.id()).toUri()).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edycja oferty (zmiana ceny → historia cen, przeliczenie nieruchomości)")
    public OfferDto update(@PathVariable Long id, @Valid @RequestBody OfferUpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Usunięcie oferty")
    @ApiResponse(responseCode = "204", description = "Usunięto")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
