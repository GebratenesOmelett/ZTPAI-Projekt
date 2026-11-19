package pl.domradar.source;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import pl.domradar.auth.CurrentUser;
import pl.domradar.ingestion.ImportService;
import pl.domradar.ingestion.ImportTrigger;
import pl.domradar.ingestion.dto.ImportJobDto;
import pl.domradar.source.dto.AdapterDto;
import pl.domradar.source.dto.SourceDto;
import pl.domradar.source.dto.SourceRequest;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin: źródła i importy", description = "Zarządzanie portalami i uruchamianie importów (rola ADMIN)")
public class AdminSourceController {

    private final SourceService sourceService;
    private final ImportService importService;

    @GetMapping("/sources")
    @Operation(summary = "Lista źródeł")
    public List<SourceDto> list() {
        return sourceService.list();
    }

    @GetMapping("/sources/{id}")
    @Operation(summary = "Szczegóły źródła")
    @ApiResponse(responseCode = "404", description = "Brak źródła")
    public SourceDto get(@PathVariable Long id) {
        return sourceService.get(id);
    }

    @PostMapping("/sources")
    @Operation(summary = "Dodanie źródła")
    @ApiResponse(responseCode = "201", description = "Utworzono (nagłówek Location)")
    @ApiResponse(responseCode = "409", description = "Kod zajęty (SOURCE_CODE_TAKEN)")
    @ApiResponse(responseCode = "422", description = "Nieznany adapter (UNKNOWN_ADAPTER)")
    public ResponseEntity<SourceDto> create(@Valid @RequestBody SourceRequest request) {
        SourceDto created = sourceService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/sources/{id}")
    @Operation(summary = "Aktualizacja źródła (włączenie/wyłączenie, harmonogram cron, adapter)")
    public SourceDto update(@PathVariable Long id, @Valid @RequestBody SourceRequest request) {
        return sourceService.update(id, request);
    }

    @DeleteMapping("/sources/{id}")
    @Operation(summary = "Usunięcie źródła bez ofert")
    @ApiResponse(responseCode = "204", description = "Usunięto")
    @ApiResponse(responseCode = "409", description = "Źródło ma oferty (SOURCE_IN_USE)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sourceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/adapters")
    @Operation(summary = "Zarejestrowane adaptery źródeł")
    public List<AdapterDto> adapters() {
        return sourceService.adapters();
    }

    @PostMapping("/sources/{id}/imports")
    @Operation(summary = "Uruchomienie importu (asynchronicznie przez RabbitMQ)",
            description = "Zwraca 202 Accepted i nagłówek Location do zadania. Postęp jest publikowany na żywo przez WebSocket.")
    @ApiResponse(responseCode = "202", description = "Zadanie przyjęte do kolejki")
    @ApiResponse(responseCode = "409", description = "Import tego źródła już trwa (IMPORT_ALREADY_RUNNING)")
    @ApiResponse(responseCode = "422", description = "Źródło wyłączone (SOURCE_DISABLED) lub pobieranie na żywo zablokowane (LIVE_FETCH_DISABLED)")
    public ResponseEntity<ImportJobDto> startImport(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        ImportJobDto job = importService.requestImport(id, ImportTrigger.MANUAL, user.email());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/admin/import-jobs/{jobId}").buildAndExpand(job.id()).toUri();
        return ResponseEntity.accepted().location(location).body(job);
    }
}
