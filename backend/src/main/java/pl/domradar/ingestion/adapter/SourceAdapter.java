package pl.domradar.ingestion.adapter;

import pl.domradar.source.Source;

import java.util.stream.Stream;

/**
 * Plugin pobierający ogłoszenia z jednego typu źródła. Implementacje są beanami Springa
 * i rejestrowane automatycznie w {@link AdapterRegistry} po swoim kodzie
 * (kolumna {@code source.adapter}).
 */
public interface SourceAdapter {

    /** Kod adaptera, np. "demo", "snapshot", "otodom-http". */
    String code();

    /** Krótki opis wyświetlany w panelu administratora. */
    String description();

    /**
     * Sprawdza, czy adapter może zostać uruchomiony dla danego źródła (np. czy pobieranie na żywo
     * jest włączone flagą). Rzuca {@code DomainException}, jeśli nie.
     */
    default void checkAvailable(Source source) {
    }

    /** Strumień surowych ogłoszeń. Strumień jest zamykany przez wywołującego. */
    Stream<RawListing> fetch(ImportContext ctx);
}
