package pl.domradar.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.domradar.ingestion.ImportStatus;
import pl.domradar.ingestion.ImportTrigger;

import java.time.Instant;

@Schema(description = "Zadanie importu ogłoszeń ze źródła")
public record ImportJobDto(
        @Schema(example = "12") Long id,
        @Schema(example = "1") Long sourceId,
        @Schema(example = "otodom") String sourceCode,
        @Schema(example = "Otodom") String sourceName,
        @Schema(example = "RUNNING") ImportStatus status,
        @Schema(example = "MANUAL") ImportTrigger trigger,
        String requestedBy,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        @Schema(description = "Liczba pobranych ogłoszeń") int fetched,
        @Schema(description = "Nowe nieruchomości") int created,
        @Schema(description = "Zaktualizowane oferty") int updated,
        @Schema(description = "Oferty dołączone do istniejących nieruchomości (deduplikacja)") int merged,
        @Schema(description = "Oferty dezaktywowane (3 importy bez wystąpienia)") int deactivated,
        int errors,
        Integer expectedBatches,
        int processedBatches,
        int failedBatches,
        int attempts,
        String errorMessage) {
}
