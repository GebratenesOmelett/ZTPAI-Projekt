package pl.domradar.property;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.domradar.auth.CurrentUser;
import pl.domradar.property.dto.PropertyDetailsDto;

import java.util.List;

@RestController
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
@Tag(name = "Nieruchomości", description = "Szczegóły nieruchomości i porównywarka (publiczne)")
public class PropertyController {

    private final PropertyQueryService queryService;

    @GetMapping("/{id}")
    @Operation(summary = "Szczegóły nieruchomości: oferty z portali, historia cen, zdjęcia, porównanie do mediany dzielnicy")
    @ApiResponse(responseCode = "200", description = "Szczegóły")
    @ApiResponse(responseCode = "404", description = "Nie znaleziono")
    public PropertyDetailsDto details(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        return queryService.details(id, user == null ? null : user.id());
    }

    @GetMapping("/compare")
    @Operation(summary = "Porównanie do 4 nieruchomości obok siebie")
    @ApiResponse(responseCode = "400", description = "Brak identyfikatorów lub więcej niż 4")
    public List<PropertyDetailsDto> compare(
            @Parameter(description = "Identyfikatory (1-4)", example = "1,2,3") @RequestParam List<Long> ids,
            @AuthenticationPrincipal CurrentUser user) {
        return queryService.compare(ids, user == null ? null : user.id());
    }
}
