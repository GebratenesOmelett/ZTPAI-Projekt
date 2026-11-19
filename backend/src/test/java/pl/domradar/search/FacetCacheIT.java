package pl.domradar.search;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import pl.domradar.common.cache.SearchCacheInvalidator;
import pl.domradar.property.TransactionType;
import pl.domradar.search.dto.SearchFacets;
import pl.domradar.support.IntegrationTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Fasety są zapisywane w Redisie pod kluczem = SHA-256 kryteriów i usuwane przez invalidację (np. po imporcie). */
@IntegrationTest
class FacetCacheIT {

    @Autowired
    FacetService facetService;
    @Autowired
    StringRedisTemplate redis;
    @Autowired
    SearchCacheInvalidator invalidator;

    @Test
    void facetsAreCachedInRedisAndInvalidated() {
        SearchCriteria criteria = SearchCriteria.builder().transactionType(TransactionType.RENT).rooms(List.of(2)).build();
        invalidator.invalidateAll();

        SearchFacets first = facetService.facets(criteria);
        assertThat(redis.keys("domradar:facets::*")).hasSize(1)
                .allSatisfy(key -> assertThat(key).matches("domradar:facets::[0-9a-f]{64}"));
        assertThat(redis.getExpire(redis.keys("domradar:facets::*").iterator().next())).isBetween(1L, 300L);

        SearchFacets second = facetService.facets(SearchCriteria.builder().rooms(List.of(2)).transactionType(TransactionType.RENT).build());
        assertThat(second).isEqualTo(first);
        assertThat(redis.keys("domradar:facets::*")).hasSize(1);

        invalidator.invalidateAll();
        assertThat(redis.keys("domradar:facets::*")).isEmpty();
    }
}
