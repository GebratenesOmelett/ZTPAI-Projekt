package pl.domradar.ingestion.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import pl.domradar.ingestion.adapter.ImportContext;
import pl.domradar.ingestion.adapter.RawListing;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * SZKIELET adaptera HTTP dla Otodom. Domyślnie wyłączony ({@code app.sources.otodom-live.live-enabled=false}),
 * ponieważ regulamin portalu nie zezwala na automatyczne pobieranie treści. Pokazuje, jak wyglądałaby integracja
 * przy posiadaniu zgody / oficjalnego API: pojedyncza strona wyników, odczyt osadzonego JSON-a strony,
 * mapowanie na {@link RawListing}. Nie był testowany na żywym portalu.
 */
@Component
public class OtodomHttpAdapter extends HttpPortalAdapter {

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
        return "Szkielet adaptera HTTP (robots.txt, limit żądań, User-Agent) — domyślnie wyłączony";
    }

    @Override
    public Stream<RawListing> fetch(ImportContext ctx) {
        String html = get(ctx.sourceCode(), config(ctx.sourceCode()).startUrl());
        if (html == null) {
            return Stream.empty();
        }
        Element data = Jsoup.parse(html).selectFirst("script#__NEXT_DATA__");
        if (data == null) {
            return Stream.empty();
        }
        try {
            JsonNode items = objectMapper.readTree(data.data()).at("/props/pageProps/data/searchAds/items");
            List<RawListing> out = new ArrayList<>();
            for (JsonNode item : items) {
                out.add(RawListing.builder()
                        .externalId(item.path("id").asText())
                        .url(ctx.baseUrl() + "/pl/oferta/" + item.path("slug").asText())
                        .title(item.path("title").asText(null))
                        .priceText(item.path("totalPrice").path("value").asText(null))
                        .currency(item.path("totalPrice").path("currency").asText("PLN"))
                        .areaText(item.path("areaInSquareMeters").asText(null))
                        .roomsText(item.path("roomsNumber").asText(null))
                        .floorText(item.path("floorNumber").asText(null))
                        .propertyTypeText(item.path("estate").asText(null))
                        .transactionText(item.path("transaction").asText(null))
                        .city(item.path("location").path("address").path("city").path("name").asText(null))
                        .district(item.path("location").path("address").path("district").path("name").asText(null))
                        .street(item.path("location").path("address").path("street").path("name").asText(null))
                        .agencyName(item.path("agency").path("name").asText(null))
                        .sellerText(item.path("isPrivateOwner").asBoolean(false) ? "prywatny" : "biuro")
                        .photos(item.path("images").findValuesAsText("large"))
                        .build());
            }
            return out.stream();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse Otodom page", e);
        }
    }
}
