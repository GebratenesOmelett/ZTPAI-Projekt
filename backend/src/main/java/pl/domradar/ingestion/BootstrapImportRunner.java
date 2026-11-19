package pl.domradar.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pl.domradar.property.PropertyRepository;
import pl.domradar.source.Source;
import pl.domradar.source.SourceRepository;

import java.util.Set;

/**
 * Przy pierwszym uruchomieniu (pusta baza) zleca import ze źródeł demo i snapshot,
 * aby {@code docker compose up} od razu dawał aplikację z danymi.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class BootstrapImportRunner implements ApplicationRunner {

    private static final Set<String> BOOTSTRAP_ADAPTERS = Set.of("demo", "snapshot");

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
            if (!source.isEnabled() || !BOOTSTRAP_ADAPTERS.contains(source.getAdapter())) {
                continue;
            }
            try {
                importService.requestImport(source.getId(), ImportTrigger.BOOTSTRAP, "bootstrap");
            } catch (RuntimeException e) {
                log.warn("Bootstrap import of {} not started: {}", source.getCode(), e.getMessage());
            }
        }
    }
}
