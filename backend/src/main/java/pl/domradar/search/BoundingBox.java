package pl.domradar.search;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Prostokąt widocznego obszaru mapy (WGS84)")
public record BoundingBox(
        @NotNull @DecimalMin("-90") @DecimalMax("90") @Schema(example = "52.15") Double south,
        @NotNull @DecimalMin("-180") @DecimalMax("180") @Schema(example = "20.90") Double west,
        @NotNull @DecimalMin("-90") @DecimalMax("90") @Schema(example = "52.30") Double north,
        @NotNull @DecimalMin("-180") @DecimalMax("180") @Schema(example = "21.15") Double east
) {
}
