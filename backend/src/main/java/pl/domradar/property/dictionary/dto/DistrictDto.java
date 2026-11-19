package pl.domradar.property.dictionary.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dzielnica")
public record DistrictDto(Long id, Long cityId, @Schema(example = "Mokotów") String name,
                          @Schema(example = "mokotow") String slug, double lat, double lon) {
}
