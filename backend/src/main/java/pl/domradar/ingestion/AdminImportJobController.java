package pl.domradar.ingestion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pl.domradar.common.web.PageResponse;
import pl.domradar.ingestion.dto.ImportJobDto;

import java.time.Instant;

@Validated
@RestController
@RequestMapping("/api/v1/admin/import-jobs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin: źródła i importy")
public class AdminImportJobController {

    private final ImportService importService;

    @GetMapping
    @Operation(summary = "Lista zadań importu (paginacja + filtry), od najnowszych")
    public PageResponse<ImportJobDto> list(
            @Parameter(description = "Filtr po źródle") @RequestParam(required = false) Long sourceId,
            @Parameter(description = "Filtr po statusie") @RequestParam(required = false) ImportStatus status,
            @Parameter(description = "Utworzone od (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Utworzone do (ISO-8601)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return importService.search(sourceId, status, from, to, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Szczegóły zadania importu")
    public ImportJobDto get(@PathVariable Long id) {
        return importService.get(id);
    }
}
