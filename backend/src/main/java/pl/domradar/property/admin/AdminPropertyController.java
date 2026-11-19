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
import pl.domradar.property.PropertyStatus;
import pl.domradar.property.admin.dto.*;
import pl.domradar.property.dto.OfferDto;
import pl.domradar.property.dto.PropertyDetailsDto;

import java.net.URI;

@Validated
@RestController
@RequestMapping("/api/v1/admin/properties")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin: nieruchomości", description = "Edycja z optimistic locking, scalanie i rozdzielanie (rola ADMIN)")
public class AdminPropertyController {

    private final PropertyAdminService propertyService;
    private final OfferAdminService offerService;

    @GetMapping
    @Operation(summary = "Lista nieruchomości (wyszukiwanie po tytule/ulicy, status, miasto, paginacja)")
    public PageResponse<AdminPropertyDto> list(@RequestParam(required = false) String q,
                                               @RequestParam(required = false) PropertyStatus status,
                                               @RequestParam(required = false) Long cityId,
                                               @RequestParam(defaultValue = "0") @Min(0) int page,
                                               @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return propertyService.list(q, status, cityId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastSeenAt")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Szczegóły nieruchomości (z wersją do edycji)")
    public PropertyDetailsDto get(@PathVariable Long id) {
        return propertyService.get(id);
    }

    @PostMapping
    @Operation(summary = "Utworzenie nieruchomości z ofertą początkową (bez deduplikacji)")
    @ApiResponse(responseCode = "201", description = "Utworzono")
    public ResponseEntity<PropertyDetailsDto> create(@Valid @RequestBody OfferCreateRequest request) {
        OfferDto offer = offerService.create(request, true);
        PropertyDetailsDto created = propertyService.get(offer.propertyId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edycja atrybutów nieruchomości (optimistic locking)",
            description = "Wymaga pola version z ostatniego odczytu. Jeśli nieruchomość została w międzyczasie zmieniona → 409 VERSION_CONFLICT.")
    @ApiResponse(responseCode = "200", description = "Zapisano (nowa wersja w odpowiedzi)")
    @ApiResponse(responseCode = "409", description = "Konflikt wersji (VERSION_CONFLICT)")
    @ApiResponse(responseCode = "422", description = "Dzielnica nie należy do miasta (INVALID_REFERENCE)")
    public PropertyDetailsDto update(@PathVariable Long id, @Valid @RequestBody PropertyUpdateRequest request) {
        return propertyService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Usunięcie nieruchomości wraz z ofertami")
    @ApiResponse(responseCode = "204", description = "Usunięto")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        propertyService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/merge")
    @Operation(summary = "Scalenie innej nieruchomości z tą (ręczna deduplikacja)")
    @ApiResponse(responseCode = "409", description = "Konflikt wersji")
    @ApiResponse(responseCode = "422", description = "Scalenie niedozwolone (MERGE_NOT_ALLOWED)")
    public PropertyDetailsDto merge(@PathVariable Long id, @Valid @RequestBody MergeRequest request) {
        return propertyService.merge(id, request);
    }

    @PostMapping("/{id}/split")
    @Operation(summary = "Rozdzielenie: wskazane oferty trafiają do nowej nieruchomości")
    @ApiResponse(responseCode = "201", description = "Utworzono nową nieruchomość (Location)")
    @ApiResponse(responseCode = "422", description = "Rozdzielenie niedozwolone (SPLIT_NOT_ALLOWED)")
    public ResponseEntity<SplitResponse> split(@PathVariable Long id, @Valid @RequestBody SplitRequest request) {
        SplitResponse response = propertyService.split(id, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/admin/properties/{id}").buildAndExpand(response.created().id()).toUri();
        return ResponseEntity.created(location).body(response);
    }
}
