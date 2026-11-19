package pl.domradar.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;
import pl.domradar.common.exception.DomainException;
import pl.domradar.source.Source;
import pl.domradar.source.SourceRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Scheduler importów: co minutę sprawdza wyrażenie cron każdego włączonego źródła (kolumna source.import_cron)
 * i zleca import, gdy nadszedł jego termin. Dzięki temu zmiana harmonogramu w panelu admina działa od razu,
 * bez restartu aplikacji. Dodatkowo watchdog oznacza "zawieszone" zadania jako FAILED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportScheduler {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    private final SourceRepository sourceRepository;
    private final ImportJobRepository jobRepository;
    private final ImportService importService;
    private final ImportJobService jobService;
    private final IngestionProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void triggerDueImports() {
        if (!properties.schedulerEnabled()) {
            return;
        }
        Instant now = clock.instant();
        for (Source source : sourceRepository.findByEnabledTrue()) {
            if (source.getImportCron() == null || source.getImportCron().isBlank()
                    || !CronExpression.isValidExpression(source.getImportCron())) {
                continue;
            }
            Instant base = jobRepository.findTopBySourceIdOrderByCreatedAtDesc(source.getId())
                    .map(ImportJob::getCreatedAt).orElse(source.getCreatedAt());
            ZonedDateTime next = CronExpression.parse(source.getImportCron()).next(base.atZone(ZONE));
            if (next != null && !next.toInstant().isAfter(now)) {
                try {
                    importService.requestImport(source.getId(), ImportTrigger.SCHEDULED, "scheduler");
                } catch (DomainException e) {
                    log.debug("Scheduled import of {} skipped: {}", source.getCode(), e.getMessage());
                }
            }
        }
    }

    /** Zadania, których worker nie podjął w 15 min lub które trwają ponad 2 h, są oznaczane jako FAILED. */
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT2M")
    public void failStuckJobs() {
        Instant now = clock.instant();
        jobRepository.findByStatusAndCreatedAtBefore(ImportStatus.QUEUED, now.minus(15, ChronoUnit.MINUTES))
                .forEach(job -> jobService.markFailed(job.getId(), "Zadanie nie zostało podjęte przez workera (timeout)"));
        jobRepository.findByStatusAndStartedAtBefore(ImportStatus.RUNNING, now.minus(2, ChronoUnit.HOURS))
                .forEach(job -> jobService.markFailed(job.getId(), "Przekroczono maksymalny czas importu"));
    }
}
