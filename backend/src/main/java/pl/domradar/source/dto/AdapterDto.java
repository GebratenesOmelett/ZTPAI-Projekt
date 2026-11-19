package pl.domradar.source.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Zarejestrowany adapter źródła")
public record AdapterDto(@Schema(example = "demo") String code, String description) {
}
