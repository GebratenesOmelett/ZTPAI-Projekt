package pl.domradar.search;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.common.config.CacheConfig;
import pl.domradar.search.dto.SearchFacets;
import pl.domradar.search.query.SearchSqlBuilder;

/** Fasety wyszukiwania — kosztowne (5 zapytań agregujących), dlatego cache'owane w Redisie po haszu kryteriów. */
@Service
@RequiredArgsConstructor
public class FacetService {

    private final SearchRepository repository;

    @Cacheable(cacheNames = CacheConfig.FACETS, keyGenerator = "criteriaHashKeyGenerator")
    @Transactional(readOnly = true)
    public SearchFacets facets(SearchCriteria criteria) {
        SearchSqlBuilder b = SearchSqlBuilder.from(criteria);
        return new SearchFacets(
                repository.districtFacet(b),
                repository.roomsFacet(b),
                repository.typeFacet(b),
                repository.sourceFacet(b),
                repository.priceHistogram(b));
    }
}
