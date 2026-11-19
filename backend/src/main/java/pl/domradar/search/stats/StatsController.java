package pl.domradar.search.stats;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
@Tag(name = "Statystyki", description = "Mediany cen za m² per dzielnica (publiczne, cache Redis)")
public class StatsController {

    private final DistrictStatsService statsService;

    @GetMapping("/districts")
    @Operation(summary = "Mediana i średnia ceny za m² per dzielnica")
    public List<DistrictStatsDto> districts(
            @Parameter(description = "Filtr po mieście") @RequestParam(required = false) Long cityId,
            @Parameter(example = "APARTMENT") @RequestParam(required = false) PropertyType propertyType,
            @Parameter(example = "SALE") @RequestParam(required = false) TransactionType transactionType) {
        return statsService.list(cityId, propertyType, transactionType);
    }
}
