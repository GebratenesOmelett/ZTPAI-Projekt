package pl.domradar.property.dictionary.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Miasto")
public record CityDto(Long id, @Schema(example = "Warszawa") String name, @Schema(example = "warszawa") String slug,
                      double lat, double lon) {
}
