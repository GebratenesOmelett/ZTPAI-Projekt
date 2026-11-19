package pl.domradar.ingestion.adapter.http;

import lombok.extern.slf4j.Slf4j;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.ingestion.adapter.SourceAdapter;
import pl.domradar.source.Source;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Baza adapterów pobierających prawdziwe ogłoszenia z portali. Zasady (README, "Aspekt prawny"):
 * <ul>
 *   <li>działają tylko po jawnym włączeniu ({@code app.sources.<code>.live-enabled}),</li>
 *   <li>respektują robots.txt (ścieżka + query, wzorce RFC 9309); brak zgody = brak żądania,</li>
 *   <li>zachowują minimalny odstęp między żądaniami i przedstawiają się identyfikującym User-Agentem,</li>
 *   <li>nie logują się do portali i nie obchodzą zabezpieczeń: HTTP 403/429 kończy pobieranie (bez ponowień),</li>
 *   <li>pobierają tylko strony z wynikami wyszukiwania (bez stron kontaktowych i danych sprzedających).</li>
 * </ul>
 */
@Slf4j
public abstract class HttpPortalAdapter implements SourceAdapter {

    private static final Duration ROBOTS_TTL = Duration.ofHours(24);

    /** Portal odmówił dostępu automatom (403/429) — pobieranie zostaje przerwane, bez prób obejścia. */
    public static class AccessRefusedException extends RuntimeException {
        public AccessRefusedException(String message) {
            super(message);
        }
    }

    private record CachedRobots(RobotsTxtPolicy policy, Instant fetchedAt) {
    }

    private final LiveSourcesProperties liveProperties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<String, CachedRobots> robotsCache = new ConcurrentHashMap<>();
    private long lastRequestAt;

    protected HttpPortalAdapter(LiveSourcesProperties liveProperties) {
        this.liveProperties = liveProperties;
    }

    @Override
    public boolean fullSnapshot() {
        return false;
    }

    @Override
    public int staleDays(String sourceCode) {
        LiveSourcesProperties.LiveSource cfg = liveProperties.forCode(sourceCode);
        return cfg == null ? 14 : cfg.staleDays();
    }

    @Override
    public void checkAvailable(Source source) {
        LiveSourcesProperties.LiveSource cfg = liveProperties.forCode(source.getCode());
        if (cfg == null || !cfg.liveEnabled()) {
            throw new DomainException(ErrorCode.LIVE_FETCH_DISABLED,
                    "Pobieranie prawdziwych ogłoszeń jest wyłączone (LIVE_SOURCES_ENABLED=false, źródło " + source.getCode() + ")");
        }
    }

    protected LiveSourcesProperties.LiveSource config(String sourceCode) {
        LiveSourcesProperties.LiveSource cfg = liveProperties.forCode(sourceCode);
        if (cfg == null || !cfg.liveEnabled()) {
            throw new IllegalStateException("Live fetching disabled for " + sourceCode);
        }
        return cfg;
    }

    /**
     * Pobiera zasób z zachowaniem robots.txt i limitu częstotliwości.
     *
     * @return treść odpowiedzi; empty, gdy robots.txt zabrania albo zasób nie istnieje (404)
     * @throws AccessRefusedException przy HTTP 403/429
     */
    protected Optional<String> get(String sourceCode, String url, String accept) {
        LiveSourcesProperties.LiveSource cfg = config(sourceCode);
        URI uri = URI.create(url);
        String origin = uri.getScheme() + "://" + uri.getHost();
        String pathAndQuery = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        if (!robots(origin, cfg.userAgent()).isAllowed(pathAndQuery)) {
            log.warn("robots.txt disallows {} - skipping", url);
            return Optional.empty();
        }
        throttle(cfg.minDelayMs());
        try {
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(uri)
                    .header("User-Agent", cfg.userAgent())
                    .header("Accept", accept)
                    .header("Accept-Language", "pl-PL,pl;q=0.9")
                    .timeout(Duration.ofSeconds(25))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 403 || status == 429) {
                throw new AccessRefusedException("Portal odmówił dostępu automatycznego (HTTP " + status + ") — przerywam pobieranie");
            }
            if (status == 404) {
                return Optional.empty();
            }
            if (status >= 400) {
                throw new IllegalStateException("HTTP " + status + " dla " + url);
            }
            return Optional.of(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Nie można pobrać " + url + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Przerwano", e);
        }
    }

    private RobotsTxtPolicy robots(String origin, String userAgent) {
        CachedRobots cached = robotsCache.get(origin);
        if (cached == null || cached.fetchedAt().isBefore(Instant.now().minus(ROBOTS_TTL))) {
            cached = new CachedRobots(loadRobots(origin, userAgent), Instant.now());
            robotsCache.put(origin, cached);
        }
        return cached.policy();
    }

    private RobotsTxtPolicy loadRobots(String origin, String userAgent) {
        try {
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(URI.create(origin + "/robots.txt"))
                            .header("User-Agent", userAgent).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 500 || response.statusCode() == 403 || response.statusCode() == 429) {
                return RobotsTxtPolicy.disallowAll();
            }
            if (response.statusCode() >= 400) {
                return RobotsTxtPolicy.allowAll();
            }
            return RobotsTxtPolicy.parse(response.body(), userAgent);
        } catch (IOException e) {
            log.warn("Cannot read robots.txt of {}: {} - treating as disallow all", origin, e.getMessage());
            return RobotsTxtPolicy.disallowAll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RobotsTxtPolicy.disallowAll();
        }
    }

    private synchronized void throttle(long minDelayMs) {
        long wait = lastRequestAt + minDelayMs - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }
}
