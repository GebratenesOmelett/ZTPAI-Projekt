package pl.domradar.ingestion.adapter.http;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import pl.domradar.common.util.TextNormalizer;
import pl.domradar.ingestion.adapter.RawListing;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Szybko.pl — karty wyników z mikrodanymi schema.org (cena w {@code itemprop=price}) i współrzędnymi w atrybutach
 * odnośnika do mapy. Imię i nazwisko agenta prezentowane na karcie nie jest zapisywane.
 */
@Component
public class SzybkoAdapter extends PagedPortalAdapter {

    private static final String BASE = "https://szybko.pl";
    /** "Warszawa (Bemowo) (Mazowieckie, gm. Warszawa)" albo "Warszawa (Mokotów), Sardyńska" → miasto, dzielnica, ulica. */
    private static final Pattern ADDRESS = Pattern.compile("^([^(,]+?)\\s*(?:\\(([^)]*)\\))?\\s*(?:,\\s*([^()]+?))?\\s*(?:\\(.*\\))?$");

    public SzybkoAdapter(LiveSourcesProperties liveProperties) {
        super(liveProperties);
    }

    @Override
    public String code() {
        return "szybko-http";
    }

    @Override
    public String description() {
        return "Prawdziwe ogłoszenia z Szybko.pl (mikrodane schema.org kart wyników)";
    }

    @Override
    protected String portalName() {
        return "Szybko";
    }

    @Override
    protected String pageUrl(PortalCity city, boolean rent, int page) {
        String base = BASE + "/l/" + (rent ? "na-wynajem" : "na-sprzedaz") + "/lokal-mieszkalny/"
                + URLEncoder.encode(city.name(), StandardCharsets.UTF_8) + "?sort=newest";
        return page == 1 ? base : base + "&page=" + page;
    }

    @Override
    protected Page parsePage(String html, PortalCity city, boolean rent) {
        Document doc = Jsoup.parse(html, BASE);
        List<Element> cards = doc.select("div.gt-listing-item-asset[data-assetid]");
        List<RawListing> result = new ArrayList<>();
        for (Element card : cards) {
            toRaw(card, city, rent).ifPresent(result::add);
        }
        return new Page(result, !cards.isEmpty());
    }

    static Optional<RawListing> toRaw(Element card, PortalCity city, boolean rent) {
        Element priceEl = card.selectFirst("[itemprop=price][content]");
        String price = priceEl == null ? null : priceEl.attr("content");
        String area = HtmlText.firstNumber(HtmlText.text(card.selectFirst("li.area")));
        Element link = card.selectFirst("a.listing-title-heading[href]");
        if (price == null || price.isBlank() || price.equals("0") || area == null || link == null) {
            return Optional.empty();
        }
        Element addressEl = card.selectFirst("a.list-elem-address");
        String district = null;
        String street = null;
        Double lat = null;
        Double lon = null;
        if (addressEl != null) {
            Matcher m = ADDRESS.matcher(Optional.ofNullable(HtmlText.text(addressEl)).orElse(""));
            if (m.matches()) {
                if (!TextNormalizer.key(m.group(1)).equals(TextNormalizer.key(city.name()))) {
                    return Optional.empty();
                }
                district = m.group(2);
                street = m.group(3);
            }
            lat = parseDouble(addressEl.attr("data-lat"));
            lon = parseDouble(addressEl.attr("data-lon"));
        }
        Element currency = card.selectFirst("[itemprop=priceCurrency][content]");
        Element image = card.selectFirst("link[itemprop=image][href]");
        Element description = card.selectFirst("[itemprop=description]");
        String orgId = card.attr("data-orgId");
        return Optional.of(RawListing.builder()
                .externalId(card.attr("data-assetid"))
                .url(link.absUrl("href"))
                .title(HtmlText.text(link))
                .description(HtmlText.text(description))
                .priceText(price)
                .currency(currency == null ? "PLN" : currency.attr("content"))
                .areaText(area)
                .roomsText(HtmlText.firstNumber(HtmlText.text(card.selectFirst("li.rooms"))))
                .yearBuiltText(HtmlText.text(card.selectFirst("li.year_built")))
                .propertyTypeText("mieszkanie")
                .transactionText(rent ? "wynajem" : "sprzedaż")
                .city(city.name())
                .district(district)
                .street(street)
                .lat(lat)
                .lon(lon)
                .features(List.of())
                .photos(image == null ? List.of() : List.of(image.attr("href")))
                .sellerText(!orgId.isBlank() && !orgId.equals("0") ? "biuro" : null)
                .build());
    }

    private static Double parseDouble(String value) {
        try {
            return value == null || value.isBlank() ? null : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
