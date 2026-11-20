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
            Instant lastRun = jobRepository.findTopBySourceIdOrderByCreatedAtDesc(source.getId())
                    .map(ImportJob::getCreatedAt).orElse(source.getCreatedAt());
            if (isDue(source.getImportCron(), lastRun, now, ZONE)) {
                try {
                    importService.requestImport(source.getId(), ImportTrigger.SCHEDULED, "scheduler");
                } catch (DomainException e) {
                    log.debug("Scheduled import of {} skipped: {}", source.getCode(), e.getMessage());
                }
            }
        }
    }

    /**
     * Import jest należny, gdy od ostatniego uruchomienia minął kolejny termin z wyrażenia cron.
     * Np. dla "0 0 4 * * *" — raz dziennie o 4:00; jeśli aplikacja była wtedy wyłączona, import zostanie nadrobiony
     * przy najbliższym sprawdzeniu po starcie (ale tylko raz, nie za każdy pominięty dzień).
     */
    static boolean isDue(String cron, Instant lastRun, Instant now, ZoneId zone) {
        ZonedDateTime next = CronExpression.parse(cron).next(lastRun.atZone(zone));
        return next != null && !next.toInstant().isAfter(now);
    }

    /**
     * Zadania, których worker nie podjął w 3 h lub które trwają ponad 2 h, są oznaczane jako FAILED.
     * Limit kolejkowania uwzględnia pierwszy start z kilkunastoma źródłami: importy portali (przy wielu miastach
     * i stronach nawet kilkanaście minut każdy, z limitem żądań) czekają w kolejce na wolnego workera.
     */
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT2M")
    public void failStuckJobs() {
        Instant now = clock.instant();
        jobRepository.findByStatusAndCreatedAtBefore(ImportStatus.QUEUED, now.minus(3, ChronoUnit.HOURS))
                .forEach(job -> jobService.markFailed(job.getId(), "Zadanie nie zostało podjęte przez workera (timeout)"));
        jobRepository.findByStatusAndStartedAtBefore(ImportStatus.RUNNING, now.minus(2, ChronoUnit.HOURS))
                .forEach(job -> jobService.markFailed(job.getId(), "Przekroczono maksymalny czas importu"));
    }
}
