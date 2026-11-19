package pl.domradar.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(description = "Lekki punkt na mapę (klastrowanie po stronie klienta)")
public record MapPointDto(Long id, double lat, double lon, BigDecimal price, BigDecimal pricePerM2,
                          Integer rooms, BigDecimal area, String title) {
}
