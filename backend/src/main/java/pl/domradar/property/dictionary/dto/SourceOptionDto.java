package pl.domradar.property.dictionary.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Portal do filtrowania")
public record SourceOptionDto(@Schema(example = "otodom") String code, @Schema(example = "Otodom") String name) {
}
