package pl.domradar.ingestion.normalize;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import pl.domradar.property.MarketType;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ValueParsersTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "649 000 zł|649000",
            "649 000 zł|649000",
            "649000,00 PLN|649000.00",
            "649 000,00 zł|649000.00",
            "1.250.000 zł|1250000",
            "€ 520 000|520000",
            "3 400 zł/mies.|3400",
            "339 000 zł do negocjacji|339000",
            "54,3 m²|54.3",
            "54.30 m2|54.30",
            "1 100 m²|1100"
    })
    void parsesPolishAmounts(String text, String expected) {
        assertThat(ValueParsers.parseAmount(text)).hasValueSatisfying(v -> assertThat(v).isEqualByComparingTo(new BigDecimal(expected)));
    }

    @Test
    void amountWithoutDigitsIsEmpty() {
        assertThat(ValueParsers.parseAmount("zapytaj o cenę")).isEmpty();
        assertThat(ValueParsers.parseAmount(null)).isEmpty();
    }

    @Test
    void parsesRooms() {
        assertThat(ValueParsers.parseRooms("3 pokoje")).contains(3);
        assertThat(ValueParsers.parseRooms("Kawalerka")).contains(1);
        assertThat(ValueParsers.parseRooms("5 pokoi")).contains(5);
        assertThat(ValueParsers.parseRooms("brak")).isEmpty();
    }

    @Test
    void parsesFloors() {
        assertThat(ValueParsers.parseFloor("parter/4", null)).isEqualTo(new ValueParsers.FloorInfo(0, 4));
        assertThat(ValueParsers.parseFloor("3/10", null)).isEqualTo(new ValueParsers.FloorInfo(3, 10));
        assertThat(ValueParsers.parseFloor("4 piętro", "8")).isEqualTo(new ValueParsers.FloorInfo(4, 8));
        assertThat(ValueParsers.parseFloor("Parter", "3")).isEqualTo(new ValueParsers.FloorInfo(0, 3));
        assertThat(ValueParsers.parseFloor(null, "2")).isEqualTo(new ValueParsers.FloorInfo(null, 2));
    }

    @Test
    void parsesEnums() {
        assertThat(ValueParsers.parsePropertyType("Mieszkanie")).contains(PropertyType.APARTMENT);
        assertThat(ValueParsers.parsePropertyType("dom - bliźniak")).contains(PropertyType.HOUSE);
        assertThat(ValueParsers.parsePropertyType("działka")).contains(PropertyType.PLOT);
        assertThat(ValueParsers.parsePropertyType("lokal użytkowy")).contains(PropertyType.COMMERCIAL);
        assertThat(ValueParsers.parsePropertyType("pokój")).contains(PropertyType.ROOM);
        assertThat(ValueParsers.parseTransaction("Wynajmę")).contains(TransactionType.RENT);
        assertThat(ValueParsers.parseTransaction("na sprzedaż")).contains(TransactionType.SALE);
        assertThat(ValueParsers.parseMarket("rynek pierwotny")).contains(MarketType.PRIMARY);
        assertThat(ValueParsers.parseMarket("wtórny")).contains(MarketType.SECONDARY);
        assertThat(ValueParsers.parsePrivateSeller("Osoba prywatna")).contains(true);
        assertThat(ValueParsers.parsePrivateSeller("biuro nieruchomości")).contains(false);
        assertThat(ValueParsers.parseYear("2008 r.")).contains(2008);
    }
}
