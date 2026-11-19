package pl.domradar.ingestion.adapter.snapshot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.domradar.ingestion.IngestionProperties;
import pl.domradar.ingestion.adapter.ImportContext;
import pl.domradar.ingestion.adapter.RawListing;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotAdapterTest {

    private final SnapshotAdapter adapter = new SnapshotAdapter(
            new IngestionProperties(false, 50, 3, false, "../data/snapshots", null, null), new ObjectMapper());

    @Test
    void parsesJsonAndHtmlSnapshotsFromDataDirectory() {
        List<RawListing> listings = adapter.fetch(new ImportContext(1L, "nieruchomosci-online", "N-o",
                "https://www.nieruchomosci-online.pl", 1, Instant.now())).toList();

        assertThat(listings).hasSizeGreaterThanOrEqualTo(15);
        assertThat(listings).extracting(RawListing::externalId).contains("NO-2611001", "NO-2611101");
    }

    @Test
    void htmlCardIsMappedToRawListing() {
        RawListing raw = adapter.parseHtml("""
                <article class="listing" data-id="T-1">
                  <h1 class="listing-title">Mieszkanie testowe</h1>
                  <div class="price">500 000 zł</div>
                  <ul class="params"><li data-param="powierzchnia">40 m²</li><li data-param="pietro">2/5</li></ul>
                  <div class="location" data-lat="52.1" data-lon="21.0"><span class="city">Warszawa</span><span class="district">Wola</span></div>
                  <ul class="amenities"><li>balkon</li><li>winda</li></ul>
                  <div class="seller">osoba prywatna</div>
                </article>
                """);

        assertThat(raw.externalId()).isEqualTo("T-1");
        assertThat(raw.priceText()).isEqualTo("500 000 zł");
        assertThat(raw.floorText()).isEqualTo("2/5");
        assertThat(raw.lat()).isEqualTo(52.1);
        assertThat(raw.features()).containsExactly("balkon", "winda");
        assertThat(raw.agencyName()).isNull();
    }
}
