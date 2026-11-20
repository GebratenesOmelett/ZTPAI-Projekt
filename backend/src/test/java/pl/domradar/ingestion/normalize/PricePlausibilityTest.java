package pl.domradar.ingestion.normalize;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Odrzucanie cen, które nie są ceną oferty (przykłady z prawdziwych importów). */
class PricePlausibilityTest {

    @ParameterizedTest(name = "{0} {1} za {2} zł / {3} m² → odrzucona: {4}")
    @CsvSource({
            // "Mieszkanie za opiekę", "cena 1 zł"
            "APARTMENT,  RENT, 1,      45,  true",
            "APARTMENT,  SALE, 1,      45,  true",
            "PLOT,       SALE, 50,     900, true",
            // cena za dobę lub za osobę w kategorii najmu mieszkań
            "APARTMENT,  RENT, 150,    24,  true",
            "HOUSE,      RENT, 300,    60,  true",
            "APARTMENT,  RENT, 700,    200, true",
            // zwykły najem i sprzedaż
            "APARTMENT,  RENT, 900,    25,  false",
            "APARTMENT,  RENT, 2900,   64,  false",
            "APARTMENT,  SALE, 16500,  51,  false",
            // pokój i lokal użytkowy mogą mieć niski czynsz (np. miejsce w pokoju, magazynek)
            "ROOM,       RENT, 250,    12,  false",
            "COMMERCIAL, RENT, 200,    10,  false",
    })
    void rejectsSymbolicPerNightAndPerPersonPrices(PropertyType type, TransactionType transaction, BigDecimal price,
                                                    BigDecimal area, boolean rejected) {
        assertThat(PricePlausibility.rejectionReason(type, transaction, price, area).isPresent()).isEqualTo(rejected);
    }
}
