package pl.domradar.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Fasety z liczebnościami. Każda faseta jest liczona z pominięciem własnego filtra (faceting rozłączny),
 * dzięki czemu przy zaznaczonej dzielnicy widać też liczby dla pozostałych dzielnic.
 */
@Schema(description = "Fasety wyników wyszukiwania")
public record SearchFacets(
        List<FacetValue> districts,
        List<FacetValue> rooms,
        List<FacetValue> propertyTypes,
        List<FacetValue> sources,
        List<HistogramBucket> priceHistogram) {

    @Schema(description = "Wartość fasety")
    public record FacetValue(
            @Schema(example = "12") String key,
            @Schema(example = "Mokotów") String label,
            @Schema(example = "57") long count) {
    }

    @Schema(description = "Kubełek histogramu cen")
    public record HistogramBucket(
            @Schema(example = "500000") BigDecimal from,
            @Schema(example = "600000") BigDecimal to,
            @Schema(example = "31") long count) {
    }
}
