package pl.domradar.ingestion.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.domradar.ingestion.adapter.RawListing;
import pl.domradar.ingestion.normalize.DictionaryLookup;

import java.util.ArrayList;
import java.util.List;

/** Adapter portalu, którego strony wyników zawierają oferty w JSON-LD (schema.org). */
public abstract class SchemaOrgPortalAdapter extends PagedPortalAdapter {

    private final SchemaOrgListings listings;
    private final boolean localityIsDistrict;

    protected SchemaOrgPortalAdapter(LiveSourcesProperties liveProperties, ObjectMapper objectMapper,
                                     DictionaryLookup dictionary, boolean localityIsDistrict) {
        super(liveProperties);
        this.listings = new SchemaOrgListings(objectMapper, dictionary);
        this.localityIsDistrict = localityIsDistrict;
    }

    @Override
    protected Page parsePage(String html, PortalCity city, boolean rent) {
        List<JsonNode> offers = listings.extractOffers(html);
        List<RawListing> result = new ArrayList<>();
        for (JsonNode offer : offers) {
            listings.toRaw(offer, city, rent, localityIsDistrict).ifPresent(result::add);
        }
        return new Page(result, !offers.isEmpty());
    }
}
