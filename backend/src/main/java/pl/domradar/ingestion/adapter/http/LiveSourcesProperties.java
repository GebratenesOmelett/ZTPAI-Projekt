package pl.domradar.ingestion.adapter.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Ustawienia adapterów pobierających prawdziwe ogłoszenia z portali: {@code app.sources.<kod-źródła>.*}.
 * Włączane zmienną {@code LIVE_SOURCES_ENABLED} — patrz README, sekcja "Aspekt prawny".
 *
 * @param liveEnabled   czy wolno łączyć się z portalem
 * @param userAgent     identyfikujący User-Agent (z adresem projektu)
 * @param minDelayMs    minimalny odstęp między żądaniami do portalu
 * @param maxPages      ile stron wyników pobrać na kombinację miasto × transakcja (najnowsze ogłoszenia)
 * @param cities        slugi miast ze słownika (np. warszawa, krakow); puste = wszystkie obsługiwane
 * @param staleDays     po ilu dniach bez wystąpienia oferta jest dezaktywowana (import częściowy)
 */
@ConfigurationProperties("app")
public record LiveSourcesProperties(Map<String, LiveSource> sources) {

    public LiveSource forCode(String code) {
        return sources == null ? null : sources.get(code);
    }

    public record LiveSource(boolean liveEnabled, String userAgent, long minDelayMs, int maxPages,
                             List<String> cities, int staleDays) {

        public LiveSource {
            if (userAgent == null || userAgent.isBlank()) {
                userAgent = "DomRadarBot/1.0";
            }
            minDelayMs = Math.max(minDelayMs, 2000);
            maxPages = maxPages <= 0 ? 2 : Math.min(maxPages, 10);
            cities = cities == null ? List.of() : List.copyOf(cities);
            staleDays = staleDays <= 0 ? 14 : staleDays;
        }

        public boolean includesCity(String slug) {
            return cities.isEmpty() || cities.contains(slug);
        }
    }
}
