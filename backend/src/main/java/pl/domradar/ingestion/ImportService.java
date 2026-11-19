package pl.domradar.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.common.web.PageResponse;
import pl.domradar.ingestion.adapter.AdapterRegistry;
import pl.domradar.ingestion.dto.ImportJobDto;
import pl.domradar.ingestion.event.ImportJobChangedEvent;
import pl.domradar.ingestion.event.ImportRequestedEvent;
import pl.domradar.source.Source;
import pl.domradar.source.SourceRepository;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;

/**
 * Zlecanie importów i odczyt zadań. Reguła biznesowa nr 4:
 * <ul>
 *   <li>wyłączonego źródła nie można importować → SOURCE_DISABLED (422),</li>
 *   <li>nie można uruchomić importu źródła, które ma zadanie QUEUED/RUNNING → IMPORT_ALREADY_RUNNING (409).</li>
 * </ul>
 * Wyścig dwóch równoległych żądań rozstrzyga blokada wiersza źródła (SELECT ... FOR UPDATE),
 * a ostatnią linią obrony jest częściowy unikalny indeks {@code ux_import_job_one_active}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportService {

    private final SourceRepository sourceRepository;
    private final ImportJobRepository jobRepository;
    private final AdapterRegistry adapterRegistry;
    private final ImportJobMapper mapper;
    private final ApplicationEventPublisher events;

    @Transactional
    public ImportJobDto requestImport(Long sourceId, ImportTrigger trigger, String requestedBy) {
        Source source = sourceRepository.findByIdForUpdate(sourceId)
                .orElseThrow(() -> DomainException.notFound("Źródło", sourceId));
        if (!source.isEnabled()) {
            throw new DomainException(ErrorCode.SOURCE_DISABLED,
                    "Źródło '" + source.getName() + "' jest wyłączone — włącz je przed uruchomieniem importu",
                    Map.of("sourceId", sourceId));
        }
        adapterRegistry.get(source.getAdapter()).checkAvailable(source);
        if (jobRepository.existsBySourceIdAndStatusIn(sourceId, EnumSet.of(ImportStatus.QUEUED, ImportStatus.RUNNING))) {
            throw alreadyRunning(source);
        }
        ImportJob job;
        try {
            job = jobRepository.saveAndFlush(new ImportJob(source, trigger, requestedBy));
        } catch (DataIntegrityViolationException e) {
            throw alreadyRunning(source);
        }
        log.info("Import job {} queued for source {} ({})", job.getId(), source.getCode(), trigger);
        events.publishEvent(new ImportRequestedEvent(job.getId()));
        ImportJobDto dto = mapper.toDto(job);
        events.publishEvent(new ImportJobChangedEvent(dto));
        return dto;
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportJobDto> search(Long sourceId, ImportStatus status, Instant from, Instant to, Pageable pageable) {
        Instant effectiveFrom = from != null ? from : Instant.EPOCH;
        Instant effectiveTo = to != null ? to : Instant.parse("9999-12-31T00:00:00Z");
        return PageResponse.of(jobRepository.search(sourceId, status, effectiveFrom, effectiveTo, pageable), mapper::toDto);
    }

    @Transactional(readOnly = true)
    public ImportJobDto get(Long id) {
        return jobRepository.findWithSourceById(id).map(mapper::toDto)
                .orElseThrow(() -> DomainException.notFound("Zadanie importu", id));
    }

    private static DomainException alreadyRunning(Source source) {
        return new DomainException(ErrorCode.IMPORT_ALREADY_RUNNING,
                "Import źródła '" + source.getName() + "' jest już w kolejce lub w trakcie",
                Map.of("sourceId", source.getId()));
    }
}
