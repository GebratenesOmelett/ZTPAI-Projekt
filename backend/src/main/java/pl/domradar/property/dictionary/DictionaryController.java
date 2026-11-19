package pl.domradar.property.dictionary;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.domradar.property.dictionary.dto.CityDto;
import pl.domradar.property.dictionary.dto.DistrictDto;
import pl.domradar.property.dictionary.dto.FeatureDto;
import pl.domradar.property.dictionary.dto.SourceOptionDto;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dictionaries")
@RequiredArgsConstructor
@Tag(name = "Słowniki", description = "Miasta, dzielnice, cechy i portale (publiczne)")
public class DictionaryController {

    private final DictionaryService dictionaryService;

    @GetMapping("/cities")
    @Operation(summary = "Lista miast")
    public List<CityDto> cities() {
        return dictionaryService.cities();
    }

    @GetMapping("/districts")
    @Operation(summary = "Lista dzielnic (opcjonalnie dla miasta)")
    public List<DistrictDto> districts(@RequestParam(required = false) Long cityId) {
        return dictionaryService.districts(cityId);
    }

    @GetMapping("/features")
    @Operation(summary = "Lista cech nieruchomości")
    public List<FeatureDto> features() {
        return dictionaryService.features();
    }

    @GetMapping("/sources")
    @Operation(summary = "Lista portali (do filtrów include/exclude)")
    public List<SourceOptionDto> sources() {
        return dictionaryService.sources();
    }
}
