package pl.domradar.savedsearch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pl.domradar.savedsearch.AlertMode;
import pl.domradar.search.SearchCriteria;

@Schema(description = "Dane zapisanego wyszukiwania (tworzenie / pełna aktualizacja)")
public record SavedSearchRequest(
        @Schema(example = "Mokotów 3 pokoje") @NotBlank @Size(max = 100) String name,
        @NotNull @Valid SearchCriteria criteria,
        @Schema(example = "true") boolean alertEnabled,
        @Schema(example = "INSTANT") AlertMode alertMode) {
}
