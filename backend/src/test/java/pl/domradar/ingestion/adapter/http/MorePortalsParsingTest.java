package pl.domradar.ingestion.adapter.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import pl.domradar.ingestion.adapter.RawListing;
import pl.domradar.ingestion.normalize.DictionaryLookup;
import pl.domradar.ingestion.normalize.ValueParsers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Parsowanie stron wyników kolejnych portali na syntetycznych fiksturach (bez sieci, bez prawdziwych ogłoszeń). */
class MorePortalsParsingTest {

    private static final PortalCity WARSZAWA = city("warszawa");
    private static final PortalCity GDANSK = city("gdansk");

    private final LiveSourcesProperties props = new LiveSourcesProperties(Map.of());
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DictionaryLookup dictionary = mock(DictionaryLookup.class);

    MorePortalsParsingTest() {
        when(dictionary.city(anyString())).thenReturn(Optional.empty());
    }

    private static PortalCity city(String slug) {
        return PortalCity.ALL.stream().filter(c -> c.slug().equals(slug)).findFirst().orElseThrow();
    }

    private static String fixture(String name) throws IOException {
        try (var in = Objects.requireNonNull(MorePortalsParsingTest.class.getResourceAsStream("/fixtures/" + name))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void schemaOrgOffersAreExtractedFromJsonLd() throws IOException {
        PagedPortalAdapter.Page page = new MorizonAdapter(props, objectMapper, dictionary)
                .parsePage(fixture("schemaorg-search.html"), WARSZAWA, false);

        // oferta bez ceny jest pomijana, niepoprawny blok JSON-LD nie przerywa parsowania
        assertThat(page.listings()).extracting(RawListing::externalId).containsExactly("1000001", "2000002");
        assertThat(page.hasMore()).isTrue();

        RawListing offer = page.listings().getFirst();
        assertThat(offer.priceText()).isEqualTo("700000");
        assertThat(offer.areaText()).isEqualTo("52.40");
        assertThat(offer.roomsText()).isEqualTo("3");
        assertThat(offer.floorText()).isEqualTo("2");
        assertThat(offer.district()).as("addressLocality to dzielnica").isEqualTo("Mokotów");
        assertThat(offer.street()).isEqualTo("Próbna");
        assertThat(offer.transactionText()).isEqualTo("sprzedaż");
        assertThat(offer.photos()).hasSize(2);
        assertThat(ValueParsers.parsePrivateSeller(offer.sellerText())).as("brak sprzedawcy + 'bez pośredników'").contains(true);

        RawListing listing = page.listings().get(1);
        assertThat(listing.priceText()).isEqualTo("900000.0");
        assertThat(listing.agencyName()).isEqualTo("Biuro Przykładowe");
        assertThat(listing.district()).as("dzielnica dopasowywana później z nazwy oferty").contains("Warszawa Wola");
    }

    @Test
    void schemaOrgLocalityOutsideCityIsSkippedWhenLocalityIsTown() {
        SchemaOrgListings listings = new SchemaOrgListings(objectMapper, dictionary);
        String html = """
                <script type="application/ld+json">{"@type":"Offer","name":"Mieszkanie 50 m²","url":"https://portal.example/o/123456",
                "price":"500000","itemOffered":{"address":{"addressLocality":"Piaseczno"}}}</script>""";
        var node = listings.extractOffers(html).getFirst();

        assertThat(listings.toRaw(node, WARSZAWA, false, false)).isEmpty();
        assertThat(listings.toRaw(node, WARSZAWA, false, true)).hasValueSatisfying(r -> assertThat(r.areaText()).isEqualTo("50"));
    }

    @Test
    void schemaOrgHelpers() {
        assertThat(SchemaOrgListings.externalId("https://www.morizon.pl/oferta/sprzedaz-mieszkanie-52m2-mzn2048413630")).isEqualTo("2048413630");
        assertThat(SchemaOrgListings.externalId("https://portal.example/oferta/abc-def/")).isEqualTo("abc-def");
        assertThat(SchemaOrgListings.transaction("Mieszkanie do wynajęcia", "", false)).isEqualTo("wynajem");
        assertThat(SchemaOrgListings.transaction("Mieszkanie", "", true)).isEqualTo("wynajem");
        assertThat(SchemaOrgListings.areaFromText("Kawalerka, 28,5 m² Podgórze")).isEqualTo("28,5");
    }

    @Test
    void portalUrlsFollowPortalConventions() {
        assertThat(new MorizonAdapter(props, objectMapper, dictionary).pageUrl(WARSZAWA, true, 2))
                .isEqualTo("https://www.morizon.pl/do-wynajecia/mieszkania/warszawa/?page=2");
        assertThat(new DomiportaAdapter(props, objectMapper, dictionary).pageUrl(WARSZAWA, false, 1))
                .isEqualTo("https://www.domiporta.pl/mieszkanie/sprzedam/mazowieckie/warszawa");
        assertThat(new SzybkoAdapter(props).pageUrl(city("lodz"), false, 2))
                .isEqualTo("https://szybko.pl/l/na-sprzedaz/lokal-mieszkalny/%C5%81%C3%B3d%C5%BA?sort=newest&page=2");
        assertThat(new AdresowoAdapter(props).pageUrl(WARSZAWA, true, 3)).isEqualTo("https://adresowo.pl/mieszkania-wynajem/warszawa/_l3");
        assertThat(new SprzedajemyAdapter(props).pageUrl(WARSZAWA, false, 3))
                .isEqualTo("https://sprzedajemy.pl/warszawa/nieruchomosci/mieszkania/sprzedaz?offset=60");
        TrojmiastoAdapter trojmiasto = new TrojmiastoAdapter(props);
        assertThat(trojmiasto.pageUrl(GDANSK, false, 2)).endsWith("/mieszkanie/?strona=1");
        assertThat(trojmiasto.pageUrl(GDANSK, true, 1)).as("brak listy wynajmu").isNull();
        assertThat(trojmiasto.pageUrl(WARSZAWA, false, 1)).as("tylko Trójmiasto").isNull();
        assertThat(trojmiasto.pageUrl(city("gdynia"), false, 1)).as("lista wspólna z Gdańskiem — bez drugiego pobrania").isNull();
        assertThat(OtodomHttpAdapter.CITY_PATHS)
                .containsEntry("bialystok", "podlaskie/bialystok/bialystok/bialystok")
                .hasSize(PortalCity.ALL.size());
        assertThat(OlxApiAdapter.CITY_IDS.keySet()).as("OLX obsługuje wszystkie miasta")
                .containsExactlyElementsOf(PortalCity.ALL.stream().map(PortalCity::slug).toList());
    }

    @Test
    void getHomeStateIsParsedWithoutAgentData() throws IOException {
        PagedPortalAdapter.Page page = new GetHomeAdapter(props, objectMapper).parsePage(fixture("gethome-search.html"), WARSZAWA, false);

        // oferta spoza miasta i z ukrytą ceną są pomijane
        assertThat(page.listings()).extracting(RawListing::externalId).containsExactly("aaaa-1111", "bbbb-2222");

        RawListing sale = page.listings().getFirst();
        assertThat(sale.url()).isEqualTo("https://gethome.pl/oferta/sprzedam-mieszkanie-warszawa-48m2-1000/");
        assertThat(sale.title()).isEqualTo("Słoneczne mieszkanie {z balkonem}");
        assertThat(sale.description()).isEqualTo("Opis \"w cudzysłowie\"");
        assertThat(sale.priceText()).isEqualTo("650000");
        assertThat(sale.currency()).isEqualTo("PLN");
        assertThat(sale.areaText()).isEqualTo("48.5");
        assertThat(sale.floorText()).isEqualTo("3");
        assertThat(sale.totalFloorsText()).isEqualTo("5");
        assertThat(sale.district()).isEqualTo("Ochota");
        assertThat(sale.marketText()).isEqualTo("wtórny");
        assertThat(sale.lat()).isEqualTo(52.2);
        assertThat(sale.agencyName()).isEqualTo("Agencja Przykładowa");
        assertThat(sale.toString()).doesNotContain("Jan Testowy").doesNotContain("600000000");

        RawListing rent = page.listings().get(1);
        assertThat(rent.transactionText()).isEqualTo("wynajem");
        assertThat(rent.district()).as("osiedle, gdy brak dzielnicy").isEqualTo("Tarchomin");
        assertThat(rent.agencyName()).as("osoba prywatna").isNull();
        assertThat(ValueParsers.parsePrivateSeller(rent.sellerText())).contains(true);
        assertThat(rent.marketText()).isEqualTo("pierwotny");
    }

    @Test
    void szybkoCardsAreParsedWithoutAgentName() throws IOException {
        PagedPortalAdapter.Page page = new SzybkoAdapter(props).parsePage(fixture("html-portals.html"), WARSZAWA, false);

        assertThat(page.listings()).as("oferta z innej miejscowości jest pomijana").hasSize(1);
        RawListing r = page.listings().getFirst();
        assertThat(r.externalId()).isEqualTo("3000001");
        assertThat(r.url()).isEqualTo("https://szybko.pl/o/na-sprzedaz/lokal-mieszkalny/Warszawa+Wola/oferta-3000001");
        assertThat(r.priceText()).isEqualTo("610000");
        assertThat(r.areaText()).isEqualTo("45");
        assertThat(r.roomsText()).isEqualTo("2");
        assertThat(r.yearBuiltText()).isEqualTo("2012");
        assertThat(r.district()).isEqualTo("Wola");
        assertThat(r.street()).isEqualTo("Testowa");
        assertThat(r.lat()).isEqualTo(52.23);
        assertThat(r.photos()).containsExactly("https://img.example/sz1.jpg");
        assertThat(r.toString()).doesNotContain("Imię Agenta");
        assertThat(ValueParsers.parsePrivateSeller(r.sellerText())).contains(false);
    }

    @Test
    void adresowoCardsUseUnitsToRecognizeValues() throws IOException {
        PagedPortalAdapter.Page page = new AdresowoAdapter(props).parsePage(fixture("html-portals.html"), WARSZAWA, false);

        assertThat(page.hasMore()).isTrue();
        assertThat(page.listings()).as("cena 'zapytaj' jest pomijana").extracting(RawListing::externalId).containsExactly("4000001", "4000003");
        assertThat(ValueParsers.parsePrivateSeller(page.listings().get(1).sellerText())).as("przez agenta").contains(false);
        RawListing r = page.listings().getFirst();
        assertThat(r.externalId()).isEqualTo("4000001");
        assertThat(r.url()).isEqualTo("https://adresowo.pl/o/mieszkanie-warszawa-praga-poludnie-ul-testowa-x1");
        assertThat(ValueParsers.parseAmount(r.priceText())).hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("799000"));
        assertThat(r.areaText()).isEqualTo("37");
        assertThat(r.roomsText()).isEqualTo("2");
        assertThat(r.district()).isEqualTo("Praga-Południe");
        assertThat(r.street()).isEqualTo("ul. Testowa 1");
        assertThat(r.title()).isEqualTo("Mieszkanie na sprzedaż, Warszawa Praga-Południe, ul. Testowa 1");
        assertThat(ValueParsers.parsePrivateSeller(r.sellerText())).contains(true);
    }

    @Test
    void sprzedajemyCardsAreParsed() throws IOException {
        PagedPortalAdapter.Page page = new SprzedajemyAdapter(props).parsePage(fixture("html-portals.html"), WARSZAWA, false);

        assertThat(page.listings()).extracting(RawListing::externalId).containsExactly("5000001", "5000003");
        assertThat(page.listings().get(1).district()).as("bez dzielnicy w karcie — tytuł do dopasowania").isEqualTo("Mieszkanie 70 m2 Wola");
        RawListing r = page.listings().getFirst();
        assertThat(r.url()).isEqualTo("https://sprzedajemy.pl/sprzedam-3-pokojowe-mieszkanie-testowe-nr5000001");
        assertThat(ValueParsers.parseAmount(r.priceText())).hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("819000"));
        assertThat(r.areaText()).isEqualTo("49");
        assertThat(r.roomsText()).as("z tytułu").isEqualTo("3");
        assertThat(r.district()).isEqualTo("Ursus");
        assertThat(ValueParsers.parsePrivateSeller(r.sellerText())).contains(true);
    }

    @Test
    void sprzedajemyFixesMiscategorizedRentOffers() throws IOException {
        PagedPortalAdapter.Page page = new SprzedajemyAdapter(props).parsePage(fixture("html-portals.html"), WARSZAWA, true);

        // "Sprzedam ..." w kategorii wynajmu to sprzedaż; "najem" za 450 000 zł bez wskazówki w tytule jest odrzucany
        assertThat(page.listings()).extracting(RawListing::externalId).containsExactly("5000001");
        assertThat(page.listings().getFirst().transactionText()).isEqualTo("sprzedaż");
    }

    @Test
    void trojmiastoCardsGetCityFromSubtitle() throws IOException {
        PagedPortalAdapter.Page page = new TrojmiastoAdapter(props).parsePage(fixture("html-portals.html"), GDANSK, false);

        assertThat(page.hasMore()).isTrue();
        // wspólna lista Gdańska i Gdyni pobierana raz; miasto z podtytułu karty, Sopot pomijany
        assertThat(page.listings()).extracting(RawListing::externalId).containsExactly("6000001", "6000002", "6000003");
        assertThat(page.listings()).extracting(RawListing::city).containsExactly("Gdańsk", "Gdynia", "Gdańsk");
        assertThat(page.listings().get(1).district()).isEqualTo("Orłowo");
        assertThat(page.listings().get(2).photos()).as("leniwe zdjęcie: data-src zamiast grafiki zastępczej")
                .containsExactly("https://img.example/tr3.jpg");
        assertThat(page.listings().get(1).photos()).as("bez zdjęcia — grafika zastępcza nie jest zapisywana").isEmpty();
        RawListing r = page.listings().getFirst();
        assertThat(ValueParsers.parseAmount(r.priceText())).hasValueSatisfying(p -> assertThat(p).isEqualByComparingTo("1165000"));
        assertThat(r.areaText()).isEqualTo("81.35");
        assertThat(ValueParsers.parseRooms(r.roomsText())).contains(3);
        assertThat(ValueParsers.parseFloor(r.floorText(), null).floor()).isZero();
        assertThat(ValueParsers.parseYear(r.yearBuiltText())).contains(1910);
        assertThat(r.district()).isEqualTo("Śródmieście");
        assertThat(r.street()).isEqualTo("Testowa");
        assertThat(r.agencyName()).isEqualTo("Biuro Trójmiejskie");
    }

    @Test
    void nieruchomosciOnlineDistrictComesFromHtmlTile() throws IOException {
        Map<String, String> districts = NieruchomosciOnlineAdapter.districtsByUrl(Jsoup.parse(fixture("html-portals.html")));
        assertThat(districts).containsEntry("https://portal.example/nieruchomosci/sprzedam-mieszkanie-warszawa-wola-testowa-60m2/2000002", "Odolany");

        String combined = fixture("schemaorg-search.html").replace("<body></body>",
                "<body>" + fixture("html-portals.html").replaceAll("(?s)^.*?(<div class=\"tertiary__box\">.*?</div>).*$", "$1") + "</body>");
        List<RawListing> listings = new NieruchomosciOnlineAdapter(props, objectMapper, dictionary).parsePage(combined, WARSZAWA, false).listings();
        assertThat(listings).filteredOn(l -> l.externalId().equals("2000002")).singleElement()
                .extracting(RawListing::district).isEqualTo("Odolany");
    }
}
