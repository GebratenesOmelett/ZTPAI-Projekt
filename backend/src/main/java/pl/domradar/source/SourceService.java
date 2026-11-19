package pl.domradar.source;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.ingestion.adapter.AdapterRegistry;
import pl.domradar.property.OfferRepository;
import pl.domradar.source.dto.AdapterDto;
import pl.domradar.source.dto.SourceDto;
import pl.domradar.source.dto.SourceRequest;

import java.util.List;
import java.util.Map;

/** CRUD źródeł (portali) dla administratora. */
@Service
@RequiredArgsConstructor
public class SourceService {

    private final SourceRepository sourceRepository;
    private final OfferRepository offerRepository;
    private final AdapterRegistry adapterRegistry;
    private final SourceMapper mapper;

    @Transactional(readOnly = true)
    public List<SourceDto> list() {
        return sourceRepository.findAllByOrderByNameAsc().stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SourceDto get(Long id) {
        return mapper.toDto(find(id));
    }

    @Transactional
    public SourceDto create(SourceRequest request) {
        validateAdapter(request.adapter());
        if (sourceRepository.existsByCode(request.code())) {
            throw codeTaken(request.code());
        }
        Source source = new Source();
        mapper.update(source, normalize(request));
        return mapper.toDto(sourceRepository.save(source));
    }

    @Transactional
    public SourceDto update(Long id, SourceRequest request) {
        Source source = find(id);
        validateAdapter(request.adapter());
        if (sourceRepository.existsByCodeAndIdNot(request.code(), id)) {
            throw codeTaken(request.code());
        }
        mapper.update(source, normalize(request));
        return mapper.toDto(source);
    }

    /** Źródła z ofertami nie można usunąć (utrata historii) — należy je wyłączyć. */
    @Transactional
    public void delete(Long id) {
        Source source = find(id);
        if (offerRepository.existsBySourceId(id)) {
            throw new DomainException(ErrorCode.SOURCE_IN_USE,
                    "Źródło ma zaimportowane oferty — zamiast usuwać, wyłącz je", Map.of("sourceId", id));
        }
        sourceRepository.delete(source);
    }

    public List<AdapterDto> adapters() {
        return adapterRegistry.all().stream().map(a -> new AdapterDto(a.code(), a.description())).toList();
    }

    private Source find(Long id) {
        return sourceRepository.findById(id).orElseThrow(() -> DomainException.notFound("Źródło", id));
    }

    private void validateAdapter(String adapter) {
        if (!adapterRegistry.exists(adapter)) {
            adapterRegistry.get(adapter);
        }
    }

    private static SourceRequest normalize(SourceRequest r) {
        String cron = r.importCron() == null || r.importCron().isBlank() ? null : r.importCron().trim();
        return new SourceRequest(r.code().trim(), r.name().trim(), r.baseUrl().trim(), r.adapter().trim(), r.enabled(), cron);
    }

    private static DomainException codeTaken(String code) {
        return new DomainException(ErrorCode.SOURCE_CODE_TAKEN, "Źródło o kodzie '" + code + "' już istnieje");
    }
}
