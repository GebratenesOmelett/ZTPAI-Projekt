package pl.domradar.ingestion.adapter.http;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Ustawienia adapterów pobierających dane na żywo: {@code app.sources.<code>.*}.
 * Domyślnie {@code live-enabled=false} — patrz sekcja "Aspekt prawny" w README.
 */
@ConfigurationProperties("app")
public record LiveSourcesProperties(Map<String, LiveSource> sources) {

    public LiveSource forCode(String code) {
        return sources == null ? null : sources.get(code);
    }

    public record LiveSource(boolean liveEnabled, String userAgent, long minDelayMs, String startUrl) {
    }
}
