package pl.domradar.ingestion.adapter.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import pl.domradar.ingestion.adapter.RawListing;
import pl.domradar.ingestion.normalize.DictionaryLookup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nieruchomości-online — serwisy miejskie (warszawa.nieruchomosci-online.pl itd.) z ofertami schema.org.
 * Lista miasta zawiera też oferty z miejscowości podmiejskich — te są pomijane.
 */
@Component
public class NieruchomosciOnlineAdapter extends SchemaOrgPortalAdapter {

    public NieruchomosciOnlineAdapter(LiveSourcesProperties liveProperties, ObjectMapper objectMapper, DictionaryLookup dictionary) {
        super(liveProperties, objectMapper, dictionary, false);
    }

    @Override
    public String code() {
        return "nieruchomosci-online-http";
    }

    @Override
    public String description() {
        return "Prawdziwe ogłoszenia z Nieruchomości-online (serwisy miejskie, JSON-LD)";
    }

    @Override
    protected String portalName() {
        return "Nieruchomości-online";
    }

    @Override
    protected String pageUrl(PortalCity city, boolean rent, int page) {
        String base = "https://" + city.slug() + ".nieruchomosci-online.pl/mieszkania," + (rent ? "wynajem" : "sprzedaz") + "/";
        return page == 1 ? base : base + "?p=" + page;
    }

    /**
     * JSON-LD portalu podaje tylko miasto i ulicę; dzielnica/osiedle jest w kafelku HTML oferty
     * ({@code p.province}: "Szczęśliwice, Warszawa") — uzupełniana po adresie oferty.
     */
    @Override
    protected Page parsePage(String html, PortalCity city, boolean rent) {
        Page page = super.parsePage(html, city, rent);
        Map<String, String> districts = districtsByUrl(Jsoup.parse(html));
        List<RawListing> listings = page.listings().stream()
                .map(l -> districts.containsKey(l.url()) ? l.toBuilder().district(districts.get(l.url())).build() : l)
                .toList();
        return new Page(listings, page.hasMore());
    }

    static Map<String, String> districtsByUrl(Document doc) {
        Map<String, String> result = new HashMap<>();
        for (Element box : doc.select(".tertiary__box")) {
            Element link = box.selectFirst("h2.name a[href]");
            Element district = box.selectFirst("p.province > a");
            if (link != null && district != null) {
                String name = district.text().replace(",", "").trim();
                if (!name.isEmpty()) {
                    result.put(link.attr("href"), name);
                }
            }
        }
        return result;
    }
}
