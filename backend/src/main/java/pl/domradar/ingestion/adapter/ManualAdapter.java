package pl.domradar.ingestion.adapter;

import org.springframework.stereotype.Component;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.source.Source;

import java.util.stream.Stream;

/** "Adapter" źródła z ofertami dodawanymi ręcznie przez administratora — nie obsługuje importu. */
@Component
public class ManualAdapter implements SourceAdapter {

    @Override
    public String code() {
        return "manual";
    }

    @Override
    public String description() {
        return "Oferty dodawane ręcznie w panelu administratora (bez importu)";
    }

    @Override
    public void checkAvailable(Source source) {
        throw new DomainException(ErrorCode.SOURCE_DISABLED, "Źródło z ręcznie dodanymi ofertami nie obsługuje importu");
    }

    @Override
    public Stream<RawListing> fetch(ImportContext ctx) {
        return Stream.empty();
    }
}
