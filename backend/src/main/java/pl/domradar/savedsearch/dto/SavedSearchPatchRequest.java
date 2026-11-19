package pl.domradar.savedsearch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import pl.domradar.savedsearch.AlertMode;

@Schema(description = "Częściowa aktualizacja: nazwa i/lub ustawienia alertu (pola null są pomijane)")
public record SavedSearchPatchRequest(
        @Size(min = 1, max = 100) String name,
        Boolean alertEnabled,
        AlertMode alertMode) {
}
