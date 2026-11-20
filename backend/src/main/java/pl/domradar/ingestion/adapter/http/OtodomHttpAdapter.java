package pl.domradar.ingestion.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import pl.domradar.ingestion.adapter.ImportContext;
import pl.domradar.ingestion.adapter.RawListing;

import java.util.*;
import java.util.stream.Stream;

/**
 * Pobiera najnowsze ogłoszenia z publicznych stron wyników Otodom (sprzedaż i wynajem mieszkań w miastach ze słownika).
 * Strona wyników zawiera dane ogłoszeń w osadzonym JSON-ie ({@code script#__NEXT_DATA__}), więc nie parsujemy
 * kruchego HTML-a. Pobierane są wyłącznie strony wyników (dozwolone w robots.txt) — bez stron kontaktowych,
 * numerów telefonów i danych osób prywatnych.
 */
@Slf4j
@Component
public class OtodomHttpAdapter extends HttpPortalAdapter {

    static final String BASE = "https://www.otodom.pl";

    /** Slug miasta w słowniku → ścieżka lokalizacji w adresach Otodom (województwo/powiat/gmina/miejscowość). */
    static final Map<String, String> CITY_PATHS = new LinkedHashMap<>();

    static {
        // miasta na prawach powiatu: powiat, gmina i miejscowość mają ten sam slug
        PortalCity.ALL.forEach(c -> CITY_PATHS.put(c.slug(), c.voivodeship() + "/" + c.slug() + "/" + c.slug() + "/" + c.slug()));
    }

    private static final Map<String, String> TRANSACTIONS = Map.of("sprzedaz", "sprzedaż", "wynajem", "wynajem");
    private static final Map<String, String> ESTATES = Map.of(
            "FLAT", "mieszkanie", "HOUSE", "dom", "TERRAIN", "działka", "COMMERCIAL_PROPERTY", "lokal użytkowy", "ROOM", "pokój");
    private static final List<String> NUMBER_WORDS = List.of(
            "ZERO", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN");
    private static final List<String> FLOOR_WORDS = List.of(
            "GROUND", "FIRST", "SECOND", "THIRD", "FOURTH", "FIFTH", "SIXTH", "SEVENTH", "EIGHTH", "NINTH", "TENTH");

    record SearchPage(List<RawListing> listings, int totalPages) {
    }

    private final ObjectMapper objectMapper;

    public OtodomHttpAdapter(LiveSourcesProperties liveProperties, ObjectMapper objectMapper) {
        super(liveProperties);
        this.objectMapper = objectMapper;
    }

    @Override
    public String code() {
        return "otodom-http";
    }

    @Override
    public String description() {
        return "Prawdziwe ogłoszenia z Otodom (strony wyników: robots.txt, limit żądań, User-Agent)";
    }

    @Override
    public Stream<RawListing> fetch(ImportContext ctx) {
        LiveSourcesProperties.LiveSource cfg = config(ctx.sourceCode());
        List<RawListing> all = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        try {
            for (Map.Entry<String, String> city : CITY_PATHS.entrySet()) {
                if (!cfg.includesCity(city.getKey())) {
                    continue;
                }
                for (Map.Entry<String, String> tx : TRANSACTIONS.entrySet()) {
                    for (int page = 1; page <= cfg.maxPages(); page++) {
                        String url = BASE + "/pl/wyniki/" + tx.getKey() + "/mieszkanie/" + city.getValue()
                                + "?by=LATEST&direction=DESC&page=" + page;
                        Optional<String> html = get(ctx.sourceCode(), url, "text/html");
                        if (html.isEmpty()) {
                            break;
                        }
                        SearchPage result;
                        try {
                            result = parseSearchPage(html.get());
                        } catch (RuntimeException e) {
                            log.warn("Cannot parse Otodom page {}: {}", url, e.getMessage());
                            break;
                        }
                        result.listings().stream().filter(l -> seen.add(l.externalId())).forEach(all::add);
                        if (result.listings().isEmpty() || page >= result.totalPages()) {
                            break;
                        }
                    }
                }
            }
        } catch (AccessRefusedException e) {
            log.warn("Otodom: {} (zebrano {} ogłoszeń przed przerwaniem)", e.getMessage(), all.size());
        }
        log.info("Otodom: pobrano {} ogłoszeń", all.size());
        return all.stream();
    }

    /** Parsuje stronę wyników: dane z osadzonego JSON-a __NEXT_DATA__. */
    SearchPage parseSearchPage(String html) {
        Element script = Jsoup.parse(html).selectFirst("script#__NEXT_DATA__");
        if (script == null) {
            throw new IllegalStateException("brak __NEXT_DATA__ na stronie wyników");
        }
        JsonNode searchAds;
        try {
            searchAds = objectMapper.readTree(script.data()).at("/props/pageProps/data/searchAds");
        } catch (Exception e) {
            throw new IllegalStateException("niepoprawny JSON strony wyników", e);
        }
        List<RawListing> listings = new ArrayList<>();
        for (JsonNode item : searchAds.path("items")) {
            toRaw(item).ifPresent(listings::add);
        }
        return new SearchPage(listings, searchAds.path("pagination").path("totalPages").asInt(1));
    }

    static Optional<RawListing> toRaw(JsonNode item) {
        String estate = item.path("estate").asText("");
        JsonNode price = item.path("totalPrice");
        // INVESTMENT = cała inwestycja dewelopera (zakres cen/metraży), hidePrice = "zapytaj o cenę"
        if (!ESTATES.containsKey(estate) || item.path("hidePrice").asBoolean(false) || price.isMissingNode()
                || price.isNull() || !price.path("value").isNumber() || !item.path("areaInSquareMeters").isNumber()) {
            return Optional.empty();
        }
        String city = null;
        String district = null;
        String residential = null;
        for (JsonNode loc : item.path("location").path("reverseGeocoding").path("locations")) {
            switch (loc.path("locationLevel").asText("")) {
                case "city_or_village" -> city = loc.path("name").asText(null);
                case "district" -> district = loc.path("name").asText(null);
                case "residential" -> residential = loc.path("name").asText(null);
                default -> {
                }
            }
        }
        boolean privateOwner = item.path("isPrivateOwner").asBoolean(false);
        String agency = item.path("agency").path("name").asText(null);
        List<String> photos = new ArrayList<>();
        for (JsonNode img : item.path("images")) {
            String url = img.path("large").asText(img.path("url").asText(null));
            if (url != null) {
                photos.add(url);
            }
        }
        return Optional.of(RawListing.builder()
                .externalId(item.path("id").asText())
                .url(BASE + "/pl/oferta/" + item.path("slug").asText())
                .title(item.path("title").asText("").trim())
                .description(textOrNull(item.path("shortDescription")))
                .priceText(price.path("value").asText())
                .currency(price.path("currency").asText("PLN"))
                .areaText(item.path("areaInSquareMeters").asText())
                .roomsText(enumNumber(item.path("roomsNumber").asText(null), NUMBER_WORDS, "MORE"))
                .floorText(floor(item.path("floorNumber").asText(null)))
                .propertyTypeText(ESTATES.get(estate))
                .transactionText("RENT".equals(item.path("transaction").asText()) ? "wynajem" : "sprzedaż")
                .marketText(item.hasNonNull("developmentId") ? "pierwotny" : null)
                .city(city)
                .district(district != null ? district : residential)
                .street(item.path("location").path("address").path("street").path("name").asText(null))
                .features(List.of())
                .photos(photos.stream().limit(10).toList())
                .agencyName(privateOwner ? null : agency)
                .sellerText(privateOwner ? "prywatny" : "biuro")
                .build());
    }

    static String enumNumber(String value, List<String> words, String moreValue) {
        if (value == null) {
            return null;
        }
        int idx = words.indexOf(value);
        if (idx >= 0) {
            return String.valueOf(idx);
        }
        return moreValue.equals(value) ? String.valueOf(words.size()) : null;
    }

    static String floor(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "GROUND" -> "parter";
            case "CELLAR" -> "suterena";
            case "ABOVE_TENTH" -> "11";
            case "GARRET" -> null;
            default -> {
                int idx = FLOOR_WORDS.indexOf(value);
                yield idx >= 0 ? String.valueOf(idx) : null;
            }
        };
    }

    private static String textOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String text = Jsoup.parse(node.asText("")).text().trim();
        return text.isEmpty() ? null : text;
    }
}
