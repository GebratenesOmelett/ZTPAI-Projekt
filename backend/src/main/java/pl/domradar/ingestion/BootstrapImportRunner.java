package pl.domradar.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pl.domradar.common.exception.DomainException;
import pl.domradar.property.PropertyRepository;
import pl.domradar.source.Source;
import pl.domradar.source.SourceRepository;


/**
 * Przy pierwszym uruchomieniu (pusta baza) zleca import ze wszystkich włączonych i dostępnych źródeł
 * (demo/snapshot przy DEMO_DATA_ENABLED=true, portale przy LIVE_SOURCES_ENABLED=true),
 * aby {@code docker compose up} od razu dawał aplikację z danymi.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class BootstrapImportRunner implements ApplicationRunner {

    private final IngestionProperties properties;
    private final PropertyRepository propertyRepository;
    private final ImportJobRepository jobRepository;
    private final SourceRepository sourceRepository;
    private final ImportService importService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.bootstrapImport() || propertyRepository.count() > 0 || jobRepository.count() > 0) {
            return;
        }
        log.info("Empty database - requesting bootstrap imports");
        for (Source source : sourceRepository.findAllByOrderByNameAsc()) {
            if (!source.isEnabled()) {
                continue;
            }
            try {
                importService.requestImport(source.getId(), ImportTrigger.BOOTSTRAP, "bootstrap");
            } catch (DomainException e) {
                // np. dane demo wyłączone (DEMO_DATA_ENABLED=false) lub portale live wyłączone (LIVE_SOURCES_ENABLED=false)
                log.info("Bootstrap import of {} skipped: {}", source.getCode(), e.getMessage());
            } catch (RuntimeException e) {
                log.warn("Bootstrap import of {} not started: {}", source.getCode(), e.getMessage());
            }
        }
    }
}
