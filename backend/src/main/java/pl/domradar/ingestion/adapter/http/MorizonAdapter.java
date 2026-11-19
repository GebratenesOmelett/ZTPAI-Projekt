package pl.domradar.ingestion.adapter.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pl.domradar.ingestion.normalize.DictionaryLookup;

/**
 * Morizon — oferty schema.org ({@code Offer} + {@code Accommodation}) osadzone na stronach wyników.
 * robots.txt Morizon blokuje parametr {@code page}, więc pobierana jest tylko pierwsza strona (najnowsze oferty).
 */
@Component
public class MorizonAdapter extends SchemaOrgPortalAdapter {

    public MorizonAdapter(LiveSourcesProperties liveProperties, ObjectMapper objectMapper, DictionaryLookup dictionary) {
        super(liveProperties, objectMapper, dictionary, true);
    }

    @Override
    public String code() {
        return "morizon-http";
    }

    @Override
    public String description() {
        return "Prawdziwe ogłoszenia z Morizon (JSON-LD ze stron wyników)";
    }

    @Override
    protected String portalName() {
        return "Morizon";
    }

    @Override
    protected String pageUrl(PortalCity city, boolean rent, int page) {
        String base = "https://www.morizon.pl/" + (rent ? "do-wynajecia/" : "") + "mieszkania/" + city.slug() + "/";
        return page == 1 ? base : base + "?page=" + page;
    }
}
