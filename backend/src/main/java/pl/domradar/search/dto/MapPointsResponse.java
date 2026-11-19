package pl.domradar.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Punkty na mapę")
public record MapPointsResponse(List<MapPointDto> points,
                                @Schema(description = "Liczba wszystkich pasujących nieruchomości") long total,
                                @Schema(description = "Czy lista została obcięta do limitu") boolean truncated) {
}
