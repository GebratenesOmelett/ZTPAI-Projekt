package pl.domradar.ingestion.adapter.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.domradar.ingestion.adapter.RawListing;
import pl.domradar.ingestion.normalize.ValueParsers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** Parsowanie odpowiedzi portali na syntetycznych fiksturach o strukturze zgodnej z prawdziwymi stronami (bez sieci). */
class LiveAdaptersParsingTest {

    private final LiveSourcesProperties props = new LiveSourcesProperties(Map.of());
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static String fixture(String name) throws IOException {
        try (var in = Objects.requireNonNull(LiveAdaptersParsingTest.class.getResourceAsStream("/fixtures/" + name))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void otodomSearchPageIsMappedFromEmbeddedJson() throws IOException {
        OtodomHttpAdapter.SearchPage page = new OtodomHttpAdapter(props, objectMapper).parseSearchPage(fixture("otodom-search.html"));

        // inwestycja deweloperska (zakres cen) i ogłoszenie z ukrytą ceną są pomijane
        assertThat(page.listings()).extracting(RawListing::externalId).containsExactly("1001", "1004");
        assertThat(page.totalPages()).isEqualTo(4);

        RawListing flat = page.listings().getFirst();
        assertThat(flat.url()).isEqualTo("https://www.otodom.pl/pl/oferta/testowe-mieszkanie-3-pokoje-ID1001");
        assertThat(flat.priceText()).isEqualTo("899000");
        assertThat(flat.roomsText()).isEqualTo("3");
        assertThat(flat.floorText()).isEqualTo("parter");
        assertThat(flat.city()).isEqualTo("Warszawa");
        assertThat(flat.district()).isEqualTo("Mokotów");
        assertThat(flat.street()).isEqualTo("ul. Testowa");
        assertThat(flat.transactionText()).isEqualTo("sprzedaż");
        assertThat(flat.agencyName()).isEqualTo("Biuro Testowe");
        assertThat(flat.description()).isEqualTo("Opis testowy. Kontakt 601 234 567");
        assertThat(flat.photos()).hasSize(2);

        RawListing rent = page.listings().get(1);
        assertThat(rent.transactionText()).isEqualTo("wynajem");
        assertThat(rent.district()).as("osiedle, gdy brak poziomu dzielnicy").isEqualTo("Ruczaj");
        assertThat(rent.floorText()).isEqualTo("11");
        assertThat(rent.marketText()).isEqualTo("pierwotny");
        assertThat(rent.agencyName()).isNull();
        assertThat(ValueParsers.parsePrivateSeller(rent.sellerText())).contains(true);
    }

    @Test
    void otodomEnumsAreTranslated() {
        assertThat(OtodomHttpAdapter.enumNumber("FOUR", java.util.List.of("ZERO", "ONE", "TWO", "THREE", "FOUR"), "MORE")).isEqualTo("4");
        assertThat(OtodomHttpAdapter.floor("CELLAR")).isEqualTo("suterena");
        assertThat(OtodomHttpAdapter.floor("GARRET")).isNull();
        assertThat(OtodomHttpAdapter.floor("SEVENTH")).isEqualTo("7");
    }

    @Test
    void olxApiResponseIsMappedWithoutPrivatePersonData() throws IOException {
        OlxApiAdapter.ApiPage page = new OlxApiAdapter(props, objectMapper).parsePage(fixture("olx-offers.json"), "sprzedaż");

        assertThat(page.hasNext()).isTrue();
        assertThat(page.listings()).extracting(RawListing::externalId).containsExactly("2001", "2003");

        RawListing privateOffer = page.listings().getFirst();
        assertThat(privateOffer.priceText()).isEqualTo("649000");
        assertThat(ValueParsers.parseAmount(privateOffer.areaText())).hasValueSatisfying(a -> assertThat(a).isEqualByComparingTo("48.50"));
        assertThat(privateOffer.roomsText()).isEqualTo("2 pokoje");
        assertThat(privateOffer.district()).isEqualTo("Mokotów");
        assertThat(privateOffer.lat()).isEqualTo(52.19);
        assertThat(privateOffer.features()).containsExactly("winda", "garaż/miejsce parkingowe");
        assertThat(privateOffer.photos()).containsExactly("https://example.com/photo/1;s=960x640");
        assertThat(privateOffer.agencyName()).as("imię osoby prywatnej nie jest zapisywane").isNull();
        assertThat(privateOffer.description()).contains("Mieszkanie po remoncie.");

        RawListing agencyOffer = page.listings().get(1);
        assertThat(agencyOffer.agencyName()).isEqualTo("Agencja Testowa");
        assertThat(agencyOffer.lat()).as("przybliżona lokalizacja nie jest traktowana jak dokładna").isNull();
        assertThat(agencyOffer.features()).isEmpty();
        assertThat(ValueParsers.parseRooms(agencyOffer.roomsText())).contains(1);
    }
}
