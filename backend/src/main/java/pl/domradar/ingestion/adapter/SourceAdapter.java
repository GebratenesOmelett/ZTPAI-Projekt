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

    /**
     * Czy jeden import zwraca pełny obraz źródła. Wtedy obowiązuje reguła "3 kolejne importy bez wystąpienia
     * = oferta nieaktywna". Adaptery live przeglądają tylko najnowsze strony wyników, więc dla nich oferta
     * wygasa po {@link #staleDays(String)} dniach bez wystąpienia.
     */
    default boolean fullSnapshot() {
        return true;
    }

    /** Dla importów częściowych: liczba dni bez wystąpienia, po której oferta staje się nieaktywna. */
    default int staleDays(String sourceCode) {
        return 14;
    }

    /** Strumień surowych ogłoszeń. Strumień jest zamykany przez wywołującego. */
    Stream<RawListing> fetch(ImportContext ctx);
}
