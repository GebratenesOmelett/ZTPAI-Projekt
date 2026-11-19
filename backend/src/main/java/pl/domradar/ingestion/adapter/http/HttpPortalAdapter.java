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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Baza dla adapterów pobierających dane bezpośrednio z portali. Zasady (patrz README, "Aspekt prawny"):
 * <ul>
 *   <li>domyślnie wyłączone flagą {@code app.sources.<code>.live-enabled=false},</li>
 *   <li>respektują robots.txt (brak zgody = brak pobierania),</li>
 *   <li>ograniczają częstotliwość żądań (min. odstęp między żądaniami),</li>
 *   <li>przedstawiają się identyfikującym User-Agentem,</li>
 *   <li>nie logują się do portali i nie obchodzą zabezpieczeń anty-botowych (CAPTCHA, 403/429 = koniec).</li>
 * </ul>
 */
@Slf4j
public abstract class HttpPortalAdapter implements SourceAdapter {

    private final LiveSourcesProperties liveProperties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<String, RobotsTxtPolicy> robotsCache = new ConcurrentHashMap<>();
    private volatile long lastRequestAt;

    protected HttpPortalAdapter(LiveSourcesProperties liveProperties) {
        this.liveProperties = liveProperties;
    }

    @Override
    public void checkAvailable(Source source) {
        LiveSourcesProperties.LiveSource cfg = liveProperties.forCode(source.getCode());
        if (cfg == null || !cfg.liveEnabled()) {
            throw new DomainException(ErrorCode.LIVE_FETCH_DISABLED,
                    "Pobieranie na żywo z portalu jest wyłączone (app.sources." + source.getCode() + ".live-enabled=false)");
        }
    }

    protected LiveSourcesProperties.LiveSource config(String sourceCode) {
        LiveSourcesProperties.LiveSource cfg = liveProperties.forCode(sourceCode);
        if (cfg == null || !cfg.liveEnabled()) {
            throw new IllegalStateException("Live fetching disabled for " + sourceCode);
        }
        return cfg;
    }

    /** Pobiera stronę z zachowaniem robots.txt i limitu częstotliwości. Zwraca null, jeśli robots.txt zabrania. */
    protected String get(String sourceCode, String url) {
        LiveSourcesProperties.LiveSource cfg = config(sourceCode);
        URI uri = URI.create(url);
        RobotsTxtPolicy robots = robotsCache.computeIfAbsent(uri.getScheme() + "://" + uri.getHost(),
                origin -> loadRobots(origin, cfg.userAgent()));
        if (!robots.isAllowed(uri.getRawPath() == null ? "/" : uri.getRawPath())) {
            log.warn("robots.txt disallows {} - skipping", url);
            return null;
        }
        throttle(cfg.minDelayMs());
        try {
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(uri)
                    .header("User-Agent", cfg.userAgent())
                    .header("Accept-Language", "pl-PL,pl;q=0.9")
                    .timeout(Duration.ofSeconds(20))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 403 || response.statusCode() == 429) {
                // Nie obchodzimy zabezpieczeń — kończymy import.
                throw new IllegalStateException("Portal refused automated access (HTTP " + response.statusCode() + ")");
            }
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " for " + url);
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot fetch " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", e);
        }
    }

    private RobotsTxtPolicy loadRobots(String origin, String userAgent) {
        try {
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(URI.create(origin + "/robots.txt"))
                    .header("User-Agent", userAgent).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 500) {
                return RobotsTxtPolicy.disallowAll();
            }
            if (response.statusCode() >= 400) {
                return RobotsTxtPolicy.allowAll();
            }
            return RobotsTxtPolicy.parse(response.body(), userAgent);
        } catch (IOException e) {
            return RobotsTxtPolicy.disallowAll();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RobotsTxtPolicy.disallowAll();
        }
    }

    private synchronized void throttle(long minDelayMs) {
        long wait = lastRequestAt + Math.max(1000, minDelayMs) - System.currentTimeMillis();
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
