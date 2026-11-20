package pl.domradar.ingestion.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import pl.domradar.ingestion.adapter.ImportContext;
import pl.domradar.ingestion.adapter.RawListing;

import java.util.*;
import java.util.stream.Stream;

/**
 * Pobiera najnowsze ogłoszenia mieszkań (sprzedaż i wynajem) z publicznego endpointu JSON OLX
 * {@code /api/v1/offers/}, który robots.txt OLX wprost dopuszcza ({@code Allow: /api/v1/offers/}).
 * Nazwy osób prywatnych nie są zapisywane — tylko informacja "osoba prywatna" albo nazwa firmy.
 */
@Slf4j
@Component
public class OlxApiAdapter extends HttpPortalAdapter {

    static final String API = "https://www.olx.pl/api/v1/offers/";
    private static final int PAGE_SIZE = 40;

    /** Slug miasta w słowniku → identyfikator miasta w OLX. */
    static final Map<String, Integer> CITY_IDS = new LinkedHashMap<>();

    static {
        CITY_IDS.put("warszawa", 17871);
        CITY_IDS.put("krakow", 8959);
        CITY_IDS.put("wroclaw", 19701);
        CITY_IDS.put("gdansk", 5659);
        CITY_IDS.put("poznan", 13983);
        CITY_IDS.put("lodz", 10609);
        // identyfikatory z /api/v1/friendly-links/query-params/nieruchomosci,mieszkania,<miasto>/ (dozwolone w robots.txt)
        CITY_IDS.put("szczecin", 16705);
        CITY_IDS.put("bydgoszcz", 4019);
        CITY_IDS.put("lublin", 10119);
        CITY_IDS.put("bialystok", 1079);
        CITY_IDS.put("katowice", 7691);
        CITY_IDS.put("gdynia", 5849);
        CITY_IDS.put("rzeszow", 15241);
        CITY_IDS.put("torun", 38395);
        CITY_IDS.put("kielce", 7971);
        CITY_IDS.put("olsztyn", 12673);
        CITY_IDS.put("opole", 12885);
    }

    /** Kategorie OLX: 14 = Mieszkania / Sprzedaż, 15 = Mieszkania / Wynajem. */
    private static final Map<Integer, String> CATEGORIES = Map.of(14, "sprzedaż", 15, "wynajem");

    record ApiPage(List<RawListing> listings, boolean hasNext) {
    }

    private final ObjectMapper objectMapper;

    public OlxApiAdapter(LiveSourcesProperties liveProperties, ObjectMapper objectMapper) {
        super(liveProperties);
        this.objectMapper = objectMapper;
    }

    @Override
    public String code() {
        return "olx-api";
    }

    @Override
    public String description() {
        return "Prawdziwe ogłoszenia z OLX (publiczne API ofert dozwolone w robots.txt)";
    }

    @Override
    public Stream<RawListing> fetch(ImportContext ctx) {
        LiveSourcesProperties.LiveSource cfg = config(ctx.sourceCode());
        List<RawListing> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try {
            for (Map.Entry<String, Integer> city : CITY_IDS.entrySet()) {
                if (!cfg.includesCity(city.getKey())) {
                    continue;
                }
                for (Map.Entry<Integer, String> category : CATEGORIES.entrySet()) {
                    for (int page = 0; page < cfg.maxPages(); page++) {
                        String url = API + "?offset=" + page * PAGE_SIZE + "&limit=" + PAGE_SIZE + "&category_id=" + category.getKey()
                                + "&city_id=" + city.getValue() + "&sort_by=created_at:desc";
                        Optional<String> json = get(ctx.sourceCode(), url, "application/json");
                        if (json.isEmpty()) {
                            break;
                        }
                        ApiPage result;
                        try {
                            result = parsePage(json.get(), category.getValue());
                        } catch (RuntimeException e) {
                            log.warn("Cannot parse OLX response {}: {}", url, e.getMessage());
                            break;
                        }
                        result.listings().stream().filter(l -> seen.add(l.externalId())).forEach(all::add);
                        if (!result.hasNext() || result.listings().isEmpty()) {
                            break;
                        }
                    }
                }
            }
        } catch (AccessRefusedException e) {
            log.warn("OLX: {} (zebrano {} ogłoszeń przed przerwaniem)", e.getMessage(), all.size());
        }
        log.info("OLX: pobrano {} ogłoszeń", all.size());
        return all.stream();
    }

    ApiPage parsePage(String json, String transactionText) {
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("niepoprawny JSON", e);
        }
        List<RawListing> listings = new ArrayList<>();
        for (JsonNode item : root.path("data")) {
            toRaw(item, transactionText).ifPresent(listings::add);
        }
        return new ApiPage(listings, root.path("links").hasNonNull("next"));
    }

    static Optional<RawListing> toRaw(JsonNode item, String transactionText) {
        Map<String, JsonNode> params = new HashMap<>();
        for (JsonNode p : item.path("params")) {
            params.put(p.path("key").asText(), p.path("value"));
        }
        JsonNode price = params.get("price");
        if (price == null || !price.path("value").isNumber() || params.get("m") == null) {
            return Optional.empty();
        }
        boolean business = item.path("business").asBoolean(false);
        JsonNode map = item.path("map");
        boolean preciseLocation = map.path("show_detailed").asBoolean(false) && map.path("lat").isNumber();
        List<String> features = new ArrayList<>();
        if (yes(params.get("winda"))) features.add("winda");
        if (yes(params.get("furniture"))) features.add("umeblowanie");
        String parking = label(params.get("parking"));
        if (parking != null && !parking.toLowerCase(Locale.ROOT).startsWith("brak")) features.add("garaż/miejsce parkingowe");
        List<String> photos = new ArrayList<>();
        for (JsonNode photo : item.path("photos")) {
            String link = photo.path("link").asText(null);
            if (link != null) {
                photos.add(link.replace("{width}", "960").replace("{height}", "640"));
            }
        }
        String company = item.path("user").path("company_name").asText("");
        if (company.isBlank()) {
            company = item.path("user").path("name").asText("");
        }
        return Optional.of(RawListing.builder()
                .externalId(item.path("id").asText())
                .url(item.path("url").asText(null))
                .title(item.path("title").asText("").trim())
                .description(htmlToText(item.path("description").asText(null)))
                .priceText(price.path("value").asText())
                .currency(price.path("currency").asText("PLN"))
                .areaText(label(params.get("m")))
                .roomsText(label(params.get("rooms")))
                .floorText(label(params.get("floor_select")))
                .propertyTypeText("mieszkanie")
                .transactionText(transactionText)
                .marketText(label(params.get("market")))
                .city(item.path("location").path("city").path("name").asText(null))
                .district(item.path("location").path("district").path("name").asText(null))
                .lat(preciseLocation ? map.path("lat").asDouble() : null)
                .lon(preciseLocation ? map.path("lon").asDouble() : null)
                .features(features)
                .photos(photos.stream().limit(10).toList())
                .agencyName(business && !company.isBlank() ? company.trim() : null)
                .sellerText(business ? "firma" : "osoba prywatna")
                .build());
    }

    private static String label(JsonNode value) {
        if (value == null) {
            return null;
        }
        String label = value.path("label").asText(null);
        return label != null ? label : value.path("key").asText(null);
    }

    private static boolean yes(JsonNode value) {
        String label = label(value);
        return label != null && label.equalsIgnoreCase("tak");
    }

    private static String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        return Jsoup.parse(html.replace("<br", "\n<br").replace("</p>", "</p>\n")).wholeText().replaceAll("\\n{3,}", "\n\n").trim();
    }
}
