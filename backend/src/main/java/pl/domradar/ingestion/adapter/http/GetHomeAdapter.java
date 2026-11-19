package pl.domradar.ingestion.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pl.domradar.ingestion.adapter.RawListing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GetHome — strona wyników zawiera stan aplikacji ({@code window.__INITIAL_STATE__}) z listą ofert w JSON-ie
 * (cena, metraż, pokoje, piętro, dzielnica, współrzędne). Dane agentów (imię, telefon) są pomijane.
 */
@Component
public class GetHomeAdapter extends PagedPortalAdapter {

    private static final String BASE = "https://gethome.pl";

    private final ObjectMapper objectMapper;

    public GetHomeAdapter(LiveSourcesProperties liveProperties, ObjectMapper objectMapper) {
        super(liveProperties);
        this.objectMapper = objectMapper;
    }

    @Override
    public String code() {
        return "gethome-http";
    }

    @Override
    public String description() {
        return "Prawdziwe ogłoszenia z GetHome (stan aplikacji osadzony w stronie wyników)";
    }

    @Override
    protected String portalName() {
        return "GetHome";
    }

    @Override
    protected String pageUrl(PortalCity city, boolean rent, int page) {
        String base = BASE + "/mieszkania/" + (rent ? "do-wynajecia" : "na-sprzedaz") + "/" + city.slug() + "/";
        return page == 1 ? base : base + "?page=" + page;
    }

    @Override
    protected Page parsePage(String html, PortalCity city, boolean rent) {
        JsonNode state = initialState(html);
        JsonNode offers = state.at("/offerList/offers/offers");
        List<RawListing> result = new ArrayList<>();
        for (JsonNode offer : offers) {
            toRaw(offer, city).ifPresent(result::add);
        }
        return new Page(result, offers.size() > 0);
    }

    /** Wycina obiekt JSON przypisany do {@code window.__INITIAL_STATE__} (z poprawnym liczeniem nawiasów w łańcuchach). */
    JsonNode initialState(String html) {
        int marker = html.indexOf("__INITIAL_STATE__");
        int start = marker < 0 ? -1 : html.indexOf('{', marker);
        if (start < 0) {
            throw new IllegalStateException("brak __INITIAL_STATE__");
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < html.length(); i++) {
            char c = html.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                try {
                    return objectMapper.readTree(html.substring(start, i + 1));
                } catch (Exception e) {
                    throw new IllegalStateException("niepoprawny JSON stanu", e);
                }
            }
        }
        throw new IllegalStateException("niezamknięty obiekt __INITIAL_STATE__");
    }

    static Optional<RawListing> toRaw(JsonNode offer, PortalCity city) {
        JsonNode property = offer.path("property");
        JsonNode price = offer.path("price");
        if (!offer.path("show_price").asBoolean(true) || !price.path("total").isNumber() || !property.path("size").isNumber()) {
            return Optional.empty();
        }
        JsonNode address = property.path("address_details");
        String cityName = address.path("city").asText(null);
        if (cityName != null && !cityName.equalsIgnoreCase(city.name())) {
            return Optional.empty();
        }
        String district = address.path("district").asText(null);
        String estate = address.path("housing_estate").asText(null);
        boolean privateSeller = offer.path("is_private").asBoolean(false);
        String slug = offer.path("slug").asText("");
        List<String> photos = new ArrayList<>();
        for (JsonNode pic : offer.path("pictures")) {
            String url = pic.path("o_img_500").asText(pic.path("o_img_360x171").asText(null));
            if (url != null) {
                photos.add(url);
            }
        }
        JsonNode coords = offer.path("coordinates");
        String market = offer.path("market_type").asText("");
        return Optional.of(RawListing.builder()
                .externalId(offer.path("id").asText(slug))
                .url(BASE + "/oferta/" + slug + "/")
                .title(offer.path("name").asText("").trim())
                .description(offer.path("description").asText(null))
                .priceText(price.path("total").asText())
                .currency(price.path("currency").asText("PLN").toUpperCase())
                .areaText(property.path("size").asText())
                .roomsText(property.path("room_number").isNumber() ? property.path("room_number").asText() : null)
                .floorText(property.path("floor").isNumber() ? property.path("floor").asText() : null)
                .totalFloorsText(property.path("floors").isNumber() ? property.path("floors").asText() : null)
                .yearBuiltText(property.path("building_year").isNumber() ? property.path("building_year").asText() : null)
                .propertyTypeText("mieszkanie")
                .transactionText("rent".equals(offer.path("deal_type").asText()) ? "wynajem" : "sprzedaż")
                .marketText(market.contains("primary") ? "pierwotny" : market.contains("aftermarket") ? "wtórny" : null)
                .city(city.name())
                .district(district != null ? district : estate)
                .lat(coords.path("lat").isNumber() ? coords.path("lat").asDouble() : null)
                .lon(coords.path("lon").isNumber() ? coords.path("lon").asDouble() : null)
                .features(List.of())
                .photos(photos.stream().limit(10).toList())
                .agencyName(privateSeller ? null : offer.path("agency").path("name").asText(null))
                .sellerText(privateSeller ? "prywatny" : "biuro")
                .build());
    }
}
