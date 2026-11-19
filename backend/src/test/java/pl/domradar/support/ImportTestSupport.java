package pl.domradar.support;

import org.springframework.jdbc.core.JdbcTemplate;
import pl.domradar.ingestion.ImportJobRepository;
import pl.domradar.ingestion.ImportService;
import pl.domradar.ingestion.ImportStatus;
import pl.domradar.ingestion.ImportTrigger;
import pl.domradar.ingestion.dto.ImportJobDto;
import pl.domradar.source.SourceRepository;

import java.time.Duration;

import static org.awaitility.Awaitility.await;

/** Uruchamianie importów w testach integracyjnych i czekanie na ich zakończenie (asynchronicznie przez RabbitMQ). */
public final class ImportTestSupport {

    private ImportTestSupport() {
    }

    public static ImportJobDto runImport(ImportService importService, ImportJobRepository jobs, SourceRepository sources, String code) {
        Long sourceId = sources.findByCode(code).orElseThrow().getId();
        ImportJobDto job = importService.requestImport(sourceId, ImportTrigger.MANUAL, "test");
        await().atMost(Duration.ofSeconds(120)).pollInterval(Duration.ofMillis(300))
                .until(() -> !jobs.findById(job.id()).orElseThrow().getStatus().isActive());
        return importService.get(job.id());
    }

    /** Importuje dane demo (otodom + morizon + snapshot), jeśli baza jest jeszcze pusta. Idempotentne między klasami testów. */
    public static synchronized void ensureDemoData(ImportService importService, ImportJobRepository jobs,
                                                   SourceRepository sources, JdbcTemplate jdbc) {
        Integer properties = jdbc.queryForObject("select count(*) from property", Integer.class);
        if (properties != null && properties > 0) {
            return;
        }
        for (String code : new String[]{"otodom", "morizon", "nieruchomosci-online"}) {
            ImportJobDto job = runImport(importService, jobs, sources, code);
            if (job.status() != ImportStatus.SUCCESS) {
                throw new IllegalStateException("Import " + code + " failed: " + job.errorMessage());
            }
        }
    }
}
