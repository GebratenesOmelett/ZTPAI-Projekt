package pl.domradar.ingestion.adapter.http;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import pl.domradar.common.util.TextNormalizer;
import pl.domradar.ingestion.adapter.RawListing;
import pl.domradar.ingestion.normalize.ValueParsers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sprzedajemy.pl — serwis ogłoszeń drobnych. Karta wyników: tytuł, cena, powierzchnia ("Pow.: 49 m²"),
 * miasto i dzielnica, typ sprzedawcy (osoba prywatna / firma). Liczba pokoi jest odczytywana z tytułu.
 */
@Component
public class SprzedajemyAdapter extends PagedPortalAdapter {

    private static final String BASE = "https://sprzedajemy.pl";
    private static final int PAGE_SIZE = 30;
    /** Czynsz najmu powyżej tej kwoty oznacza w praktyce błędnie skategoryzowaną ofertę sprzedaży. */
    private static final BigDecimal MAX_RENT = BigDecimal.valueOf(50_000);
    private static final Pattern ROOMS_IN_TITLE = Pattern.compile("(\\d{1,2})\\s?-?\\s?pok", Pattern.CASE_INSENSITIVE);

    public SprzedajemyAdapter(LiveSourcesProperties liveProperties) {
        super(liveProperties);
    }

    @Override
    public String code() {
        return "sprzedajemy-http";
    }

    @Override
    public String description() {
        return "Prawdziwe ogłoszenia z Sprzedajemy.pl (karty wyników HTML)";
    }

    @Override
    protected String portalName() {
        return "Sprzedajemy";
    }

    @Override
    protected String pageUrl(PortalCity city, boolean rent, int page) {
        String base = BASE + "/" + city.slug() + "/nieruchomosci/mieszkania/" + (rent ? "wynajem" : "sprzedaz");
        return page == 1 ? base : base + "?offset=" + (page - 1) * PAGE_SIZE;
    }

    @Override
    protected Page parsePage(String html, PortalCity city, boolean rent) {
        Document doc = Jsoup.parse(html, BASE);
        List<RawListing> result = new ArrayList<>();
        for (Element card : doc.select("li[id^=offer-]")) {
            toRaw(card, city, rent).ifPresent(result::add);
        }
        return new Page(result, doc.selectFirst("[rel=next]") != null);
    }

    static Optional<RawListing> toRaw(Element card, PortalCity city, boolean rent) {
        Element link = card.selectFirst("h2.title a[href]");
        String price = HtmlText.text(card.selectFirst(".pricing .price"));
        String area = null;
        for (Element attribute : card.select(".attributes .attribute")) {
            String t = HtmlText.text(attribute);
            if (t != null && TextNormalizer.fold(t).startsWith("pow")) {
                area = HtmlText.firstNumber(t);
            }
        }
        if (link == null || price == null || HtmlText.firstNumber(price) == null || area == null) {
            return Optional.empty();
        }
        String cityName = HtmlText.text(card.selectFirst(".location .city"));
        if (cityName != null && !TextNormalizer.key(cityName).equals(TextNormalizer.key(city.name()))) {
            return Optional.empty();
        }
        String title = HtmlText.text(link);
        // użytkownicy serwisu ogłoszeń drobnych zdarza się wystawiać sprzedaż w kategorii wynajmu (i odwrotnie)
        String transaction = SchemaOrgListings.transaction(Objects.toString(title, ""), link.attr("href"), rent);
        if (transaction.equals("wynajem") && ValueParsers.parseAmount(price).filter(p -> p.compareTo(MAX_RENT) > 0).isPresent()) {
            return Optional.empty();
        }
        Matcher rooms = ROOMS_IN_TITLE.matcher(Optional.ofNullable(title).orElse(""));
        Element image = card.selectFirst("img[src]");
        boolean privateSeller = card.selectFirst(".seller-type-info--private") != null;
        return Optional.of(RawListing.builder()
                .externalId(HtmlText.lastDigits(card.id()))
                .url(link.absUrl("href"))
                .title(title)
                .priceText(price)
                .currency("PLN")
                .areaText(area)
                .roomsText(rooms.find() ? rooms.group(1) : null)
                .propertyTypeText("mieszkanie")
                .transactionText(transaction)
                .city(city.name())
                // bez dzielnicy w karcie — dopasowanie dzielnicy po tytule w normalizacji
                .district(Optional.ofNullable(HtmlText.text(card.selectFirst(".location .precinct"))).orElse(title))
                .features(List.of())
                .photos(image == null ? List.of() : List.of(image.absUrl("src")))
                .sellerText(privateSeller ? "prywatny" : "firma")
                .build());
    }
}
