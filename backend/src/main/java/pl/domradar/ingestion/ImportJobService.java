package pl.domradar.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.ingestion.adapter.ImportContext;
import pl.domradar.ingestion.event.ImportCompletedEvent;
import pl.domradar.ingestion.event.ImportJobChangedEvent;
import pl.domradar.source.Source;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Przejścia stanów zadania importu wykonywane przez worker i konsumentów RabbitMQ. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportJobService {

    /** Dane potrzebne workerowi do uruchomienia adaptera. */
    public record RunInfo(ImportContext context, String adapterCode, String runToken, Long sourceId) {
    }

    private final ImportJobRepository jobRepository;
    private final ImportJobMapper mapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * Rozpoczyna (lub ponawia) próbę wykonania importu: status RUNNING, nowy run_token, wyzerowane liczniki.
     * Zwraca empty, jeśli zadanie jest już zakończone (np. zduplikowana wiadomość).
     */
    @Transactional
    public Optional<RunInfo> startRun(Long jobId) {
        Optional<ImportJob> maybeJob = jobRepository.findByIdForUpdate(jobId);
        if (maybeJob.isEmpty() || !maybeJob.get().getStatus().isActive()) {
            log.info("Import job {} not active - skipping message", jobId);
            return Optional.empty();
        }
        ImportJob job = maybeJob.get();
        Instant now = clock.instant();
        job.setStatus(ImportStatus.RUNNING);
        if (job.getStartedAt() == null) {
            job.setStartedAt(now);
        }
        job.setAttempts(job.getAttempts() + 1);
        job.setRunToken(UUID.randomUUID().toString());
        job.setExpectedBatches(null);
        job.setProcessedBatches(0);
        job.setFailedBatches(0);
        job.setFetched(0);
        job.setCreated(0);
        job.setUpdated(0);
        job.setMerged(0);
        job.setErrors(0);
        job.setErrorMessage(null);
        Source source = job.getSource();
        int runNumber = (int) jobRepository.countBySourceIdAndStatus(source.getId(), ImportStatus.SUCCESS) + 1;
        publishChanged(job);
        return Optional.of(new RunInfo(
                new ImportContext(job.getId(), source.getCode(), source.getName(), source.getBaseUrl(), runNumber, now),
                source.getAdapter(), job.getRunToken(), source.getId()));
    }

    @Transactional(readOnly = true)
    public boolean isCurrentRun(Long jobId, String runToken) {
        return jobRepository.findById(jobId)
                .filter(j -> j.getStatus() == ImportStatus.RUNNING && runToken != null && runToken.equals(j.getRunToken()))
                .isPresent();
    }

    /** Worker skończył pobieranie: znamy łączną liczbę ogłoszeń i batchy. */
    @Transactional
    public boolean fetchCompleted(Long jobId, String runToken, int fetched, int batches) {
        ImportJob job = jobRepository.findByIdForUpdate(jobId).orElse(null);
        if (job == null || job.getStatus() != ImportStatus.RUNNING || !runToken.equals(job.getRunToken())) {
            return false;
        }
        job.setFetched(fetched);
        job.setExpectedBatches(batches);
        publishChanged(job);
        return true;
    }

    @Transactional
    public boolean registerBatch(Long jobId, String runToken, int created, int updated, int merged, int errors) {
        boolean ok = jobRepository.registerBatch(jobId, runToken, created, updated, merged, errors) == 1;
        if (ok) {
            jobRepository.findWithSourceById(jobId).ifPresent(this::publishChanged);
        }
        return ok;
    }

    @Transactional
    public boolean registerFailedBatch(Long jobId, String runToken, int size) {
        return jobRepository.registerFailedBatch(jobId, runToken, size) == 1;
    }

    /** Wywoływane przez recoverer po wyczerpaniu prób (wiadomość trafia do DLQ). */
    @Transactional
    public void markFailed(Long jobId, String reason) {
        jobRepository.findByIdForUpdate(jobId).filter(j -> j.getStatus().isActive()).ifPresent(job -> {
            job.setStatus(ImportStatus.FAILED);
            job.setFinishedAt(clock.instant());
            job.setErrorMessage(truncate(reason));
            log.warn("Import job {} failed: {}", jobId, reason);
            publishChanged(job);
            events.publishEvent(new ImportCompletedEvent(job.getId(), job.getSource().getId(), ImportStatus.FAILED));
        });
    }

    public void publishChanged(ImportJob job) {
        events.publishEvent(new ImportJobChangedEvent(mapper.toDto(job)));
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
