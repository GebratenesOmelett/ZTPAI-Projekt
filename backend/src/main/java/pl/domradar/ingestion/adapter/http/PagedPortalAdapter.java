package pl.domradar.ingestion.adapter.http;

import lombok.extern.slf4j.Slf4j;
import pl.domradar.ingestion.adapter.ImportContext;
import pl.domradar.ingestion.adapter.RawListing;

import java.util.*;
import java.util.stream.Stream;

/**
 * Wspólna pętla adapterów portali ze stronicowaną listą wyników: dla każdego miasta i rodzaju transakcji
 * pobiera kolejne strony (do {@code max-pages}) przez {@link HttpPortalAdapter#get}, czyli z kontrolą robots.txt,
 * limitem żądań i przerwaniem przy 403/429. Strona zablokowana w robots.txt kończy paginację danej kombinacji.
 */
@Slf4j
public abstract class PagedPortalAdapter extends HttpPortalAdapter {

    public record Page(List<RawListing> listings, boolean hasMore) {
    }

    protected PagedPortalAdapter(LiveSourcesProperties liveProperties) {
        super(liveProperties);
    }

    /** Nazwa portalu do logów. */
    protected abstract String portalName();

    /** Adres strony wyników (strony numerowane od 1) albo null, gdy portal nie ma takiej listy. */
    protected abstract String pageUrl(PortalCity city, boolean rent, int page);

    /** Parsuje stronę wyników. */
    protected abstract Page parsePage(String html, PortalCity city, boolean rent);

    @Override
    public Stream<RawListing> fetch(ImportContext ctx) {
        LiveSourcesProperties.LiveSource cfg = config(ctx.sourceCode());
        List<RawListing> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try {
            for (PortalCity city : PortalCity.ALL) {
                if (!cfg.includesCity(city.slug())) {
                    continue;
                }
                for (boolean rent : new boolean[]{false, true}) {
                    for (int page = 1; page <= cfg.maxPages(); page++) {
                        String url = pageUrl(city, rent, page);
                        if (url == null) {
                            break;
                        }
                        Optional<String> html = get(ctx.sourceCode(), url, "text/html");
                        if (html.isEmpty()) {
                            break;
                        }
                        Page result;
                        try {
                            result = parsePage(html.get(), city, rent);
                        } catch (RuntimeException e) {
                            log.warn("{}: cannot parse {}: {}", portalName(), url, e.getMessage());
                            break;
                        }
                        int before = all.size();
                        result.listings().stream().filter(l -> seen.add(l.externalId())).forEach(all::add);
                        if (!result.hasMore() || all.size() == before) {
                            break;
                        }
                    }
                }
            }
        } catch (AccessRefusedException e) {
            log.warn("{}: {} (zebrano {} ogłoszeń przed przerwaniem)", portalName(), e.getMessage(), all.size());
        }
        log.info("{}: pobrano {} ogłoszeń", portalName(), all.size());
        return all.stream();
    }
}
