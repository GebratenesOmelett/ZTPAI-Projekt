package pl.domradar.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mapstruct.factory.Mappers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.ingestion.adapter.AdapterRegistry;
import pl.domradar.ingestion.adapter.SourceAdapter;
import pl.domradar.ingestion.event.ImportRequestedEvent;
import pl.domradar.source.Source;
import pl.domradar.source.SourceRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportServiceTest {

    @Mock
    SourceRepository sourceRepository;
    @Mock
    ImportJobRepository jobRepository;
    @Mock
    ApplicationEventPublisher events;
    @Mock
    SourceAdapter adapter;

    ImportService service;
    Source source;

    @BeforeEach
    void setUp() {
        when(adapter.code()).thenReturn("demo");
        service = new ImportService(sourceRepository, jobRepository, new AdapterRegistry(List.of(adapter)),
                Mappers.getMapper(ImportJobMapper.class), events);
        source = new Source();
        source.setId(7L);
        source.setCode("otodom");
        source.setName("Otodom");
        source.setAdapter("demo");
        source.setEnabled(true);
    }

    @Test
    void disabledSourceCannotBeImported() {
        source.setEnabled(false);
        when(sourceRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.requestImport(7L, ImportTrigger.MANUAL, "admin"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo(ErrorCode.SOURCE_DISABLED);
        verify(jobRepository, never()).saveAndFlush(any());
    }

    @Test
    void sourceWithActiveJobCannotBeImportedAgain() {
        when(sourceRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(source));
        when(jobRepository.existsBySourceIdAndStatusIn(anyLong(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.requestImport(7L, ImportTrigger.MANUAL, "admin"))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo(ErrorCode.IMPORT_ALREADY_RUNNING);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void uniqueIndexViolationFromConcurrentRequestIsTranslatedTo409() {
        when(sourceRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(source));
        when(jobRepository.existsBySourceIdAndStatusIn(anyLong(), any())).thenReturn(false);
        when(jobRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("ux_import_job_one_active"));

        assertThatThrownBy(() -> service.requestImport(7L, ImportTrigger.MANUAL, "admin"))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo(ErrorCode.IMPORT_ALREADY_RUNNING);
    }

    @Test
    void validRequestCreatesQueuedJobAndPublishesEvent() {
        when(sourceRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(source));
        when(jobRepository.existsBySourceIdAndStatusIn(anyLong(), any())).thenReturn(false);
        when(jobRepository.saveAndFlush(any())).thenAnswer(inv -> {
            ImportJob job = inv.getArgument(0);
            job.setId(99L);
            return job;
        });

        var dto = service.requestImport(7L, ImportTrigger.MANUAL, "admin@test");

        assertThat(dto.status()).isEqualTo(ImportStatus.QUEUED);
        assertThat(dto.sourceCode()).isEqualTo("otodom");
        verify(events).publishEvent(new ImportRequestedEvent(99L));
    }

    @Test
    void missingSourceGives404() {
        when(sourceRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestImport(1L, ImportTrigger.MANUAL, "admin"))
                .extracting(e -> ((DomainException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND);
    }
}
