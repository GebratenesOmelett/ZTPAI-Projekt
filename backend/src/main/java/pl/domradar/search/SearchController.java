package pl.domradar.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pl.domradar.search.dto.MapPointsResponse;
import pl.domradar.search.dto.SearchResponse;

import java.util.List;
import java.util.Set;

@Validated
@RestController
@RequestMapping("/api/v1/properties/search")
@RequiredArgsConstructor
@Tag(name = "Wyszukiwarka", description = "Zaawansowane wyszukiwanie, fasety, sortowanie wielokluczowe (publiczne)")
public class SearchController {

    private static final String SORT_DESCRIPTION = """
            Sortowanie wielokluczowe, np. `sort=pricePerM2,asc&sort=area,desc`. Pola: relevance, price, pricePerM2, area,
            addedAt, priceChangedAt, priceDrop, daysOnMarket, distance (wymaga lat/lon), vsMedian (% względem mediany dzielnicy).""";

    private final SearchService searchService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @PostMapping
    @Operation(summary = "Wyszukiwanie nieruchomości (kryteria w body)",
            description = "Zwraca stronę wyników, zastosowane sortowanie i fasety z liczebnościami (dzielnice, pokoje, typy, portale, histogram cen).")
    @RequestBody(content = @Content(examples = @ExampleObject(name = "Mokotów 2-3 pokoje do 1,2 mln, bez parteru", value = """
            {"q": "balkon -suterena", "transactionType": "SALE", "propertyTypes": ["APARTMENT"], "cityIds": [1],
             "rooms": [2, 3], "priceMax": 1200000, "notGroundFloor": true, "features": ["BALCONY", "ELEVATOR"],
             "featureMode": "ALL", "multiSource": false}""")))
    @ApiResponse(responseCode = "200", description = "Wyniki")
    @ApiResponse(responseCode = "400", description = "Niepoprawne kryteria (np. min > max, poligon > 100 punktów, promień > 50 km)")
    public SearchResponse search(
            @Valid @org.springframework.web.bind.annotation.RequestBody(required = false) SearchCriteria criteria,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = SORT_DESCRIPTION, array = @ArraySchema(schema = @Schema(example = "pricePerM2,asc")))
            @RequestParam(required = false) List<String> sort,
            @Parameter(description = "Czy zwracać fasety") @RequestParam(defaultValue = "true") boolean facets) {
        return searchService.search(criteria, page, size, sort, facets);
    }

    @GetMapping
    @Operation(summary = "Wyszukiwanie przez parametry URL (link do udostępnienia)",
            description = "Te same kryteria co w POST, jako parametry zapytania. bbox=south,west,north,east, polygon=GeoJSON.")
    public SearchResponse searchGet(
            @ParameterObject SearchParams params,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @Parameter(description = SORT_DESCRIPTION) @RequestParam(required = false) List<String> sort,
            @RequestParam(defaultValue = "true") boolean facets) {
        return searchService.search(validated(params.toCriteria(objectMapper)), page, size, sort, facets);
    }

    @PostMapping("/map")
    @Operation(summary = "Punkty na mapę dla kryteriów (do klastrowania), maks. 3000")
    public MapPointsResponse map(@Valid @org.springframework.web.bind.annotation.RequestBody(required = false) SearchCriteria criteria) {
        return searchService.mapPoints(criteria == null ? SearchCriteria.empty() : criteria);
    }

    private SearchCriteria validated(SearchCriteria criteria) {
        Set<ConstraintViolation<SearchCriteria>> violations = validator.validate(criteria);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
        return criteria;
    }
}
