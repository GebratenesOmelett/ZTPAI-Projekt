package pl.domradar.property.dictionary.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cecha nieruchomości")
public record FeatureDto(@Schema(example = "BALCONY") String code, @Schema(example = "Balkon") String name) {
}
