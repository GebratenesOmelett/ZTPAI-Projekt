package pl.domradar.search.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Wyniki wyszukiwania z paginacją i fasetami")
public record SearchResponse(
        List<PropertySummaryDto> content,
        @Schema(example = "0") int page,
        @Schema(example = "20") int size,
        @Schema(example = "342") long totalElements,
        @Schema(example = "18") int totalPages,
        @Schema(description = "Zastosowane sortowanie", example = "[\"pricePerM2,asc\", \"area,desc\"]") List<String> sort,
        SearchFacets facets) {
}
