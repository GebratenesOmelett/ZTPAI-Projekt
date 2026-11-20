package pl.domradar.ingestion.normalize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.domradar.property.PropertyType;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Błędny metraż wpisany przez ogłoszeniodawcę (przykłady z prawdziwych importów). */
class AreaPlausibilityTest {

    @ParameterizedTest(name = "{0} {1} m², tytuł \"{2}\" → {3}")
    @CsvSource(delimiter = ';', value = {
            // metraż z tytułu zamiast błędnego pola
            "APARTMENT; 5158;  2 oddzielne pokoje | 51,58m² | Blisko UM; 51.58",
            "APARTMENT; 42072; 2-pokojowe mieszkanie 42 m² | Dzielnica 19; 42.00",
            "APARTMENT; 2300;  2 pokoje / 47 m² / ul. Piłsudskiego;        47.00",
            // brak metrażu w tytule → odrzucenie
            "APARTMENT; 2400;  Biskupa Dominika, świetna lokalizacja;      ",
            "APARTMENT; 600;   Obiekt pracowniczy dla 50 osób;             ",
            // metraż w tytule też nierealny → odrzucenie
            "APARTMENT; 3138;  Sprzedam Mieszkanie Szczecin - 3 138 m²;    ",
            // w zakresie — bez zmian
            "APARTMENT; 68.2;  Przestronne 3 pokojowe;                     68.2",
            "HOUSE;     1200;  Dworek z parkiem;                           1200",
            // działki nie są sprawdzane
            "PLOT;      15000; Działka budowlana;                          15000",
    })
    void usesTitleAreaOrRejects(PropertyType type, BigDecimal area, String title, BigDecimal expected) {
        Optional<BigDecimal> result = AreaPlausibility.plausibleArea(type, area, title);
        if (expected == null) {
            assertThat(result).isEmpty();
        } else {
            assertThat(result).hasValueSatisfying(a -> assertThat(a).isEqualByComparingTo(expected));
        }
    }

    @Test
    void areaInTextIgnoresOtherNumbers() {
        assertThat(ValueParsers.areaInText("3 pok., 2. piętro, 54,5 m2, cena 699 000 zł")).contains(new BigDecimal("54.5"));
        assertThat(ValueParsers.areaInText("Kawalerka 25mkw")).contains(new BigDecimal("25"));
        assertThat(ValueParsers.areaInText("Mieszkanie 3 pokoje")).isEmpty();
    }
}
