package pl.domradar.savedsearch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.domradar.savedsearch.AlertMode;
import pl.domradar.search.SearchCriteria;

import java.time.Instant;

@Schema(description = "Zapisane wyszukiwanie")
public record SavedSearchDto(
        Long id,
        @Schema(example = "Mokotów 3 pokoje") String name,
        SearchCriteria criteria,
        boolean alertEnabled,
        AlertMode alertMode,
        Instant lastNotifiedAt,
        Instant createdAt,
        Instant updatedAt) {
}
