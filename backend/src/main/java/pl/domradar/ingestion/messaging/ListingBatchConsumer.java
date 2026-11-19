package pl.domradar.ingestion.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import pl.domradar.ingestion.ImportFinalizer;
import pl.domradar.ingestion.ImportJobService;
import pl.domradar.ingestion.ListingUpsertService;
import pl.domradar.ingestion.adapter.RawListing;
import pl.domradar.ingestion.normalize.ListingNormalizer;
import pl.domradar.ingestion.normalize.NormalizationException;

/**
 * Konsument normalizacji i deduplikacji. Błędy danych pojedynczego ogłoszenia (normalizacja, konflikt)
 * są liczone jako błędy importu; błędy infrastruktury (np. brak połączenia z bazą) przerywają batch,
 * który jest ponawiany, a po 3 próbach trafia do DLQ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListingBatchConsumer {

    private final ImportJobService jobService;
    private final ImportFinalizer finalizer;
    private final ListingNormalizer normalizer;
    private final ListingUpsertService upsertService;

    @RabbitListener(queues = RabbitConfig.BATCH_QUEUE, containerFactory = "batchListenerFactory")
    public void onBatch(ListingBatchMessage message) {
        if (!jobService.isCurrentRun(message.jobId(), message.runToken())) {
            log.debug("Ignoring stale batch {} of job {}", message.batchNo(), message.jobId());
            return;
        }
        int created = 0;
        int updated = 0;
        int merged = 0;
        int errors = 0;
        for (RawListing raw : message.listings()) {
            try {
                switch (upsertService.upsert(message.jobId(), message.sourceId(), normalizer.normalize(raw))) {
                    case CREATED -> created++;
                    case MERGED -> merged++;
                    case UPDATED -> updated++;
                    case UNCHANGED -> {
                    }
                }
            } catch (NormalizationException e) {
                errors++;
                log.debug("Listing {} rejected by normalizer: {}", raw.externalId(), e.getMessage());
            } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
                errors++;
                log.warn("Listing {} could not be saved: {}", raw.externalId(), e.getMessage());
            }
        }
        if (jobService.registerBatch(message.jobId(), message.runToken(), created, updated, merged, errors)) {
            finalizer.finalizeIfComplete(message.jobId());
        }
    }
}
