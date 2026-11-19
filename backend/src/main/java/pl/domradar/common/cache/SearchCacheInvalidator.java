package pl.domradar.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.domradar.common.config.CacheConfig;

import java.util.Optional;

/** Unieważnia cache faset i statystyk (po imporcie oraz po edycji/scaleniu nieruchomości przez admina). */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchCacheInvalidator {

    private final CacheManager cacheManager;

    public void invalidateAll() {
        for (String name : new String[]{CacheConfig.FACETS, CacheConfig.DISTRICT_STATS}) {
            try {
                Optional.ofNullable(cacheManager.getCache(name)).ifPresent(Cache::clear);
            } catch (RuntimeException e) {
                log.warn("Cannot clear cache {}: {}", name, e.getMessage());
            }
        }
        log.debug("Search caches invalidated");
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onSearchDataChanged(SearchDataChangedEvent event) {
        log.debug("Invalidating search caches: {}", event.reason());
        invalidateAll();
    }
}
