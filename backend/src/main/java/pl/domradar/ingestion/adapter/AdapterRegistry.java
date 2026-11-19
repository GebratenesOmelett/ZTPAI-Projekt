package pl.domradar.ingestion.adapter;

import org.springframework.stereotype.Component;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Rejestr adapterów źródeł — automatycznie zbiera wszystkie beany {@link SourceAdapter}. */
@Component
public class AdapterRegistry {

    private final Map<String, SourceAdapter> adapters;

    public AdapterRegistry(List<SourceAdapter> adapters) {
        this.adapters = adapters.stream()
                .collect(Collectors.toMap(SourceAdapter::code, Function.identity(), (a, b) -> {
                    throw new IllegalStateException("Duplicate adapter code: " + a.code());
                }, TreeMap::new));
    }

    public SourceAdapter get(String code) {
        SourceAdapter adapter = adapters.get(code);
        if (adapter == null) {
            throw new DomainException(ErrorCode.UNKNOWN_ADAPTER, "Nieznany adapter źródła: " + code,
                    Map.of("available", adapters.keySet()));
        }
        return adapter;
    }

    public boolean exists(String code) {
        return adapters.containsKey(code);
    }

    public Collection<SourceAdapter> all() {
        return Collections.unmodifiableCollection(adapters.values());
    }
}
