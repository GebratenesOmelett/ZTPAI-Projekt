package pl.domradar.ingestion.normalize;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Korekta rodzaju transakcji ogłoszeń wystawionych w złej kategorii (przykłady z prawdziwych importów). */
class TransactionClassifierTest {

    @ParameterizedTest(name = "{0} {1} za {2} zł / {3} m² → {4}")
    @CsvSource({
            // "sprzedaż" w cenie miesięcznego czynszu → najem
            "SALE, APARTMENT, 2900,    39.39,  RENT",
            "SALE, APARTMENT, 3600,    64,     RENT",
            "SALE, HOUSE,     6500,    120,    RENT",
            // prawdziwe tanie sprzedaże (udziały, kamienice do remontu) zostają sprzedażą
            "SALE, APARTMENT, 16500,   51,     SALE",
            "SALE, APARTMENT, 25000,   33,     SALE",
            "SALE, APARTMENT, 559000,  42.03,  SALE",
            // wartość niejednoznaczna (np. odstępne) — bez zmiany
            "SALE, APARTMENT, 8000,    30,     SALE",
            // "wynajem" w cenie sprzedaży → sprzedaż
            "RENT, APARTMENT, 450000,  70,     SALE",
            "RENT, APARTMENT, 2787878, 65,     SALE",
            // drogi najem luksusowych apartamentów zostaje najmem
            "RENT, APARTMENT, 58000,   205.47, RENT",
            "RENT, APARTMENT, 3200,    39,     RENT",
            // działki i lokale użytkowe mają inne zakresy cen — bez korekty
            "SALE, PLOT,      15000,   1000,   SALE",
            "RENT, COMMERCIAL, 90000,  60,     RENT",
    })
    void correctsMiscategorizedOffers(TransactionType declared, PropertyType type, BigDecimal price, BigDecimal area,
                                      TransactionType expected) {
        assertThat(TransactionClassifier.classify(declared, type, price, area)).isEqualTo(expected);
    }
}
