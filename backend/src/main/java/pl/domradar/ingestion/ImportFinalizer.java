package pl.domradar.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.ingestion.event.ImportCompletedEvent;
import pl.domradar.property.*;
import pl.domradar.property.lifecycle.OfferLifecyclePolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Zamyka import po przetworzeniu wszystkich batchy:
 * oferty niewidziane w tym imporcie dostają +1 do licznika pominięć i po 3 udanych importach
 * stają się nieaktywne; nieruchomości bez aktywnych ofert są archiwizowane (reguła nr 3).
 * Import z batchami w DLQ nie jest uznawany za udany i nie wpływa na cykl życia ofert.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportFinalizer {

    private final ImportJobRepository jobRepository;
    private final OfferRepository offerRepository;
    private final PropertyRecalculator recalculator;
    private final IngestionProperties properties;
    private final ImportJobService jobService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public void finalizeIfComplete(Long jobId) {
        ImportJob job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != ImportStatus.RUNNING || job.getExpectedBatches() == null
                || job.getProcessedBatches() < job.getExpectedBatches()) {
            return;
        }
        Instant now = clock.instant();
        if (job.getFailedBatches() > 0) {
            job.setStatus(ImportStatus.FAILED);
            job.setErrorMessage(job.getFailedBatches() + " batch(y) trafiło do DLQ po wyczerpaniu ponowień");
        } else {
            job.setDeactivated(applyLifecycle(job));
            job.setStatus(ImportStatus.SUCCESS);
            job.getSource().setLastImportAt(now);
        }
        job.setFinishedAt(now);
        log.info("Import job {} finished: {} (fetched={}, created={}, merged={}, updated={}, deactivated={}, errors={})",
                job.getId(), job.getStatus(), job.getFetched(), job.getCreated(), job.getMerged(), job.getUpdated(),
                job.getDeactivated(), job.getErrors());
        jobService.publishChanged(job);
        events.publishEvent(new ImportCompletedEvent(job.getId(), job.getSource().getId(), job.getStatus()));
    }

    private int applyLifecycle(ImportJob job) {
        int threshold = properties.missedImportsToDeactivate();
        Map<Long, Property> affected = new LinkedHashMap<>();
        int deactivated = 0;
        for (Offer offer : offerRepository.findBySourceIdAndActiveTrue(job.getSource().getId())) {
            if (Objects.equals(offer.getLastSeenJobId(), job.getId())) {
                continue;
            }
            if (OfferLifecyclePolicy.registerMiss(offer, threshold)) {
                deactivated++;
                affected.put(offer.getProperty().getId(), offer.getProperty());
            }
        }
        for (Property property : affected.values()) {
            PropertyRecalculator.Result result = recalculator.recalculate(property, offerRepository.findByPropertyId(property.getId()));
            if (result.archived()) {
                log.debug("Property {} archived - no active offers left", property.getId());
            }
        }
        return deactivated;
    }
}
