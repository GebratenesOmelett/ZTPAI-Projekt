package pl.domradar.search.stats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.domradar.common.cache.SearchCacheInvalidator;
import pl.domradar.ingestion.ImportStatus;
import pl.domradar.ingestion.event.ImportCompletedEvent;

/**
 * Po zakończonym imporcie (po commicie): odświeża mediany cen dzielnic, a następnie unieważnia cache
 * faset i statystyk — w tej kolejności, aby do cache nie trafiły nieaktualne statystyki.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportCompletedStatsListener {

    private final DistrictStatsService statsService;
    private final SearchCacheInvalidator cacheInvalidator;

    @TransactionalEventListener
    public void onImportCompleted(ImportCompletedEvent event) {
        if (event.status() == ImportStatus.SUCCESS) {
            statsService.refresh();
        }
        cacheInvalidator.invalidateAll();
    }
}
