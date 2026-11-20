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
 * Ogłoszenia Trojmiasto.pl — regionalny serwis Trójmiasta. Obsługiwana jest sprzedaż mieszkań (rynek wtórny).
 * Wspólna lista obejmuje Gdańsk i Gdynię, więc strony są pobierane raz (w przebiegu dla Gdańska), a miasto
 * jest ustalane dla każdej karty; oferty z Sopotu i okolic są pomijane (spoza słownika miast).
 * Paginacja parametrem {@code strona} liczonym od zera.
 */
@Component
public class TrojmiastoAdapter extends PagedPortalAdapter {

    private static final String BASE = "https://ogloszenia.trojmiasto.pl";
    /** Miasta ze wspólnej listy portalu, które są w słowniku DomRadaru. */
    private static final List<PortalCity> TRICITY = PortalCity.ALL.stream()
            .filter(c -> c.slug().equals("gdansk") || c.slug().equals("gdynia")).toList();

    public TrojmiastoAdapter(LiveSourcesProperties liveProperties) {
        super(liveProperties);
    }

    @Override
    public String code() {
        return "trojmiasto-http";
    }

    @Override
    public String description() {
        return "Prawdziwe ogłoszenia z ogloszenia.trojmiasto.pl (sprzedaż mieszkań w Gdańsku i Gdyni)";
    }

    @Override
    protected String portalName() {
        return "Trojmiasto.pl";
    }

    @Override
    protected String pageUrl(PortalCity city, boolean rent, int page) {
        if (rent || !city.slug().equals("gdansk")) {
            return null;
        }
        String base = BASE + "/nieruchomosci-sprzedam-rynek-wtorny/mieszkanie/";
        return page == 1 ? base : base + "?strona=" + (page - 1);
    }

    @Override
    protected Page parsePage(String html, PortalCity city, boolean rent) {
        Document doc = Jsoup.parse(html, BASE);
        List<Element> cards = doc.select(".list__item[data-id]");
        List<RawListing> result = new ArrayList<>();
        for (Element card : cards) {
            toRaw(card).ifPresent(result::add);
        }
        // karty spoza słownika miast są pomijane, więc o kolejnej stronie decyduje pager, a nie liczba wyników
        return new Page(result, !cards.isEmpty() && doc.selectFirst(".pages__controls__next") != null);
    }

    static Optional<RawListing> toRaw(Element card) {
        Element link = card.selectFirst("a.list__item__content__title__name[href]");
        String price = HtmlText.text(card.selectFirst(".list__item__price__value"));
        String area = HtmlText.firstNumber(detail(card, "powierzchnia"));
        if (link == null || price == null || HtmlText.firstNumber(price) == null || area == null) {
            return Optional.empty();
        }
        // "Gdańsk Śródmieście, Lawendowa"
        String subtitle = Optional.ofNullable(HtmlText.text(card.selectFirst(".list__item__content__subtitle"))).orElse("");
        int comma = subtitle.indexOf(',');
        String place = (comma >= 0 ? subtitle.substring(0, comma) : subtitle).trim();
        String street = comma >= 0 ? subtitle.substring(comma + 1).trim() : null;
        Optional<PortalCity> cityMatch = TRICITY.stream()
                .filter(c -> TextNormalizer.key(place).startsWith(TextNormalizer.key(c.name()))).findFirst();
        if (cityMatch.isEmpty()) {
            return Optional.empty();
        }
        PortalCity city = cityMatch.get();
        String district = place.substring(Math.min(city.name().length(), place.length())).trim();
        String url = link.absUrl("href");
        Element logo = card.selectFirst("img.img-logo[title]");
        String photo = photoUrl(card.selectFirst("img.list__item__img"));
        return Optional.of(RawListing.builder()
                .externalId(HtmlText.lastDigits(url.replaceAll("\\.html.*$", "")))
                .url(url)
                .title(HtmlText.text(link))
                .priceText(price)
                .currency("PLN")
                .areaText(area)
                .roomsText(detail(card, "l_pokoi"))
                .floorText(detail(card, "pietro"))
                .yearBuiltText(detail(card, "rok_budowy"))
                .propertyTypeText("mieszkanie")
                .transactionText("sprzedaż")
                .marketText("wtórny")
                .city(city.name())
                .district(district.isEmpty() ? null : district)
                .street(street == null || street.isEmpty() ? null : street)
                .features(List.of())
                .photos(photo == null ? List.of() : List.of(photo))
                .agencyName(logo == null ? null : logo.attr("title"))
                .sellerText(logo == null ? null : "biuro")
                .build());
    }

    /** Zdjęcia dalszych kart są ładowane leniwie: właściwy adres w {@code data-src}, w {@code src} grafika zastępcza. */
    static String photoUrl(Element img) {
        if (img == null) {
            return null;
        }
        for (String attr : new String[]{"data-src", "src"}) {
            String url = img.absUrl(attr);
            if (!url.isBlank() && !url.contains("placeholder")) {
                return url;
            }
        }
        return null;
    }

    private static String detail(Element card, String key) {
        return HtmlText.text(card.selectFirst("li.details--icons--element--" + key));
    }
}
