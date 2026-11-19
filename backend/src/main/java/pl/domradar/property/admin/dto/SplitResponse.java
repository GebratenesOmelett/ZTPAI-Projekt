package pl.domradar.property.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.domradar.property.dto.PropertyDetailsDto;

@Schema(description = "Wynik rozdzielenia")
public record SplitResponse(PropertyDetailsDto original, PropertyDetailsDto created) {
}
