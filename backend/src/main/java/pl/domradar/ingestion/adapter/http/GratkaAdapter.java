package pl.domradar.ingestion.adapter.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pl.domradar.ingestion.normalize.DictionaryLookup;

/**
 * Gratka — oferty schema.org osadzone na stronach wyników. robots.txt blokuje parametr {@code page},
 * więc pobierana jest pierwsza strona. Gratka i Morizon należą do tej samej grupy i często pokazują
 * te same ogłoszenia — deduplikacja łączy je w jedną nieruchomość.
 */
@Component
public class GratkaAdapter extends SchemaOrgPortalAdapter {

    public GratkaAdapter(LiveSourcesProperties liveProperties, ObjectMapper objectMapper, DictionaryLookup dictionary) {
        super(liveProperties, objectMapper, dictionary, true);
    }

    @Override
    public String code() {
        return "gratka-http";
    }

    @Override
    public String description() {
        return "Prawdziwe ogłoszenia z Gratka (JSON-LD ze stron wyników)";
    }

    @Override
    protected String portalName() {
        return "Gratka";
    }

    @Override
    protected String pageUrl(PortalCity city, boolean rent, int page) {
        String base = "https://gratka.pl/nieruchomosci/mieszkania/" + city.slug() + (rent ? "/wynajem" : "/sprzedaz");
        return page == 1 ? base : base + "?page=" + page;
    }
}
