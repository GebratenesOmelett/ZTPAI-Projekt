package pl.domradar.ingestion.adapter.http;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import pl.domradar.common.util.TextNormalizer;
import pl.domradar.ingestion.adapter.RawListing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adresowo — portal ogłoszeń bez pośredników. Karta wyników: cena / metraż / pokoje (rozpoznawane po jednostce),
 * linia lokalizacji "Miasto Dzielnica" i ulica. Oferty z ceną "zapytaj" są pomijane.
 */
@Component
public class AdresowoAdapter extends PagedPortalAdapter {

    private static final String BASE = "https://adresowo.pl";

    public AdresowoAdapter(LiveSourcesProperties liveProperties) {
        super(liveProperties);
    }

    @Override
    public String code() {
        return "adresowo-http";
    }

    @Override
    public String description() {
        return "Prawdziwe ogłoszenia z Adresowo (karty wyników HTML, oferty bez pośredników)";
    }

    @Override
    protected String portalName() {
        return "Adresowo";
    }

    @Override
    protected String pageUrl(PortalCity city, boolean rent, int page) {
        String base = BASE + "/" + (rent ? "mieszkania-wynajem" : "mieszkania") + "/" + city.slug() + "/";
        return page == 1 ? base : base + "_l" + page;
    }

    @Override
    protected Page parsePage(String html, PortalCity city, boolean rent) {
        Document doc = Jsoup.parse(html, BASE);
        List<RawListing> result = new ArrayList<>();
        for (Element card : doc.select("[data-offer-card][data-id]")) {
            toRaw(card, city, rent).ifPresent(result::add);
        }
        return new Page(result, doc.selectFirst("link[rel=next]") != null);
    }

    static Optional<RawListing> toRaw(Element card, PortalCity city, boolean rent) {
        String price = null;
        String area = null;
        String rooms = null;
        for (Element value : card.select("p > span.font-bold")) {
            Element unitEl = value.nextElementSibling();
            String unit = unitEl == null ? "" : TextNormalizer.fold(unitEl.text());
            String number = HtmlText.text(value);
            if (unit.startsWith("zl")) {
                price = number;
            } else if (unit.startsWith("m")) {
                area = number;
            } else if (unit.startsWith("pok")) {
                rooms = number;
            }
        }
        Element link = card.selectFirst("a[data-track=offer-link][href]");
        if (price == null || area == null || link == null) {
            return Optional.empty();
        }
        List<Element> lines = link.select("> span.line-clamp-1");
        String location = lines.isEmpty() ? null : HtmlText.text(lines.get(0));
        String street = lines.size() > 1 ? HtmlText.text(lines.get(1)) : null;
        if (location == null || !TextNormalizer.key(location).startsWith(TextNormalizer.key(city.name()))) {
            return Optional.empty();
        }
        String district = location.length() > city.name().length() ? location.substring(city.name().length()).trim() : null;
        String linkText = Optional.ofNullable(HtmlText.text(link)).orElse("");
        Element image = card.selectFirst("picture img[src]");
        String title = "Mieszkanie " + (rent ? "do wynajęcia" : "na sprzedaż") + ", " + location
                + (street != null ? ", " + street : "");
        return Optional.of(RawListing.builder()
                .externalId(card.attr("data-id"))
                .url(link.absUrl("href"))
                .title(title)
                .description(HtmlText.text(card.selectFirst("p.line-clamp-4")))
                .priceText(price)
                .currency("PLN")
                .areaText(area)
                .roomsText(rooms)
                .propertyTypeText("mieszkanie")
                .transactionText(rent ? "wynajem" : "sprzedaż")
                .city(city.name())
                .district(district == null || district.isBlank() ? null : district)
                .street(street)
                .features(List.of())
                .photos(image == null ? List.of() : List.of(image.absUrl("src")))
                .sellerText(sellerText(linkText))
                .build());
    }

    /** Karta zawiera etykietę "bez pośredników" albo "przez agenta". */
    static String sellerText(String cardText) {
        String folded = TextNormalizer.fold(cardText);
        if (folded.contains("bez posrednik")) {
            return "bez pośredników";
        }
        return folded.contains("agent") ? "biuro" : null;
    }
}
