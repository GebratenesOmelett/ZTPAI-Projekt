package pl.domradar.ingestion.normalize;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.domradar.ingestion.normalize.ContactRedactor.PLACEHOLDER;

class ContactRedactorTest {

    @Test
    void phoneNumbersAndEmailsAreRemoved() {
        assertThat(ContactRedactor.redact("Dzwoń 601 234 567 lub 601-234-567"))
                .isEqualTo("Dzwoń " + PLACEHOLDER + " lub " + PLACEHOLDER);
        assertThat(ContactRedactor.redact("Tel. +48 22 123 45 67")).isEqualTo("Tel. " + PLACEHOLDER);
        assertThat(ContactRedactor.redact("tel:601234567.")).isEqualTo("tel:" + PLACEHOLDER + ".");
        assertThat(ContactRedactor.redact("pisz na jan.kowalski@example.com")).isEqualTo("pisz na " + PLACEHOLDER);
    }

    @Test
    void pricesAreasAndYearsAreKept() {
        String text = "Cena 1 338 250 zł, 62,5 m², rok 2008, czynsz 650 zł, 4 pokoje, kod 00-950";
        assertThat(ContactRedactor.redact(text)).isEqualTo(text);
        assertThat(ContactRedactor.redact(null)).isNull();
    }
}
