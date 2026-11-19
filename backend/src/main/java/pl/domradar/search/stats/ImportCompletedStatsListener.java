package pl.domradar.search.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.domradar.ingestion.ImportStatus;
import pl.domradar.ingestion.event.ImportCompletedEvent;

/** Po udanym imporcie (po commicie) odświeża mediany cen dzielnic. */
@Component
@RequiredArgsConstructor
public class ImportCompletedStatsListener {

    private final DistrictStatsService statsService;

    @TransactionalEventListener
    public void onImportCompleted(ImportCompletedEvent event) {
        if (event.status() == ImportStatus.SUCCESS) {
            statsService.refresh();
        }
    }
}
