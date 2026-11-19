package pl.domradar.ingestion.adapter.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pl.domradar.ingestion.normalize.DictionaryLookup;

/** Domiporta — {@code RealEstateListing} (schema.org) w JSON-LD stron wyników; dzielnica jest w nazwie oferty. */
@Component
public class DomiportaAdapter extends SchemaOrgPortalAdapter {

    public DomiportaAdapter(LiveSourcesProperties liveProperties, ObjectMapper objectMapper, DictionaryLookup dictionary) {
        super(liveProperties, objectMapper, dictionary, false);
    }

    @Override
    public String code() {
        return "domiporta-http";
    }

    @Override
    public String description() {
        return "Prawdziwe ogłoszenia z Domiporta (JSON-LD RealEstateListing)";
    }

    @Override
    protected String portalName() {
        return "Domiporta";
    }

    @Override
    protected String pageUrl(PortalCity city, boolean rent, int page) {
        String base = "https://www.domiporta.pl/mieszkanie/" + (rent ? "wynajme" : "sprzedam") + "/" + city.voivodeship() + "/" + city.slug();
        return page == 1 ? base : base + "?PageNumber=" + page;
    }
}
