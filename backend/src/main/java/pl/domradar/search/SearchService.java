package pl.domradar.search;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.search.dto.MapPointDto;
import pl.domradar.search.dto.MapPointsResponse;
import pl.domradar.search.dto.PropertySummaryDto;
import pl.domradar.search.dto.SearchResponse;
import pl.domradar.search.query.SearchSqlBuilder;
import pl.domradar.search.query.SortField;
import pl.domradar.search.query.SortSpec;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Wyszukiwanie nieruchomości: wyniki z paginacją i sortowaniem wielokluczowym, fasety, punkty na mapę. */
@Service
@RequiredArgsConstructor
public class SearchService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_MAP_POINTS = 3000;

    private final SearchRepository repository;
    private final FacetService facetService;

    @Transactional(readOnly = true)
    public SearchResponse search(SearchCriteria criteria, int page, int size, List<String> sortTokens, boolean withFacets) {
        SearchCriteria c = criteria == null ? SearchCriteria.empty() : criteria;
        SortSpec sort = SortSpec.parse(sortTokens);
        if (sort.uses(SortField.DISTANCE) && !c.hasPoint()) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Sortowanie po odległości wymaga podania punktu (lat, lon)",
                    Map.of("field", "sort"));
        }
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageNo = Math.max(page, 0);
        SearchSqlBuilder builder = SearchSqlBuilder.from(c);
        long total = repository.count(builder);
        List<PropertySummaryDto> content = total == 0 || (long) pageNo * pageSize >= total ? List.of()
                : repository.page(builder, sort, pageSize, (long) pageNo * pageSize);
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return new SearchResponse(content, pageNo, pageSize, total, totalPages, sort.toApi(),
                withFacets ? facetService.facets(c) : null);
    }

    @Transactional(readOnly = true)
    public MapPointsResponse mapPoints(SearchCriteria criteria) {
        SearchSqlBuilder builder = SearchSqlBuilder.from(criteria);
        long total = repository.count(builder);
        List<MapPointDto> points = repository.mapPoints(builder, MAX_MAP_POINTS);
        return new MapPointsResponse(points, total, total > points.size());
    }

    public record NewMatches(long total, List<PropertySummaryDto> top) {
    }

    /** Nowe nieruchomości (pojawiły się po {@code since}) spełniające kryteria — dla dziennego digestu. */
    @Transactional(readOnly = true)
    public NewMatches newMatchesSince(SearchCriteria criteria, java.time.Instant since, int limit) {
        SearchSqlBuilder builder = SearchSqlBuilder.from(criteria).firstSeenAfter(since);
        long total = repository.count(builder);
        List<PropertySummaryDto> top = total == 0 ? List.of()
                : repository.page(builder, SortSpec.parse(List.of("addedAt,desc")), limit, 0);
        return new NewMatches(total, top);
    }

    /** Które z podanych nieruchomości spełniają kryteria (dopasowanie alertów do zapisanych wyszukiwań). */
    @Transactional(readOnly = true)
    public List<Long> matchingIds(SearchCriteria criteria, Collection<Long> propertyIds) {
        return repository.matchingIds(SearchSqlBuilder.from(criteria), propertyIds);
    }
}
