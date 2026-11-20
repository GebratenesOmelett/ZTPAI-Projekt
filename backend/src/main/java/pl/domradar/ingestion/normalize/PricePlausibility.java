package pl.domradar.ingestion.normalize;

import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Odrzuca ogłoszenia z ceną, która nie jest ceną oferty: symboliczne kwoty ("1 zł", "za opiekę") oraz ceny
 * za dobę lub za osobę wystawione w kategorii najmu mieszkań ("noclegi", "200 m² dla 28 osób" za 700 zł).
 * Takie oferty psułyby sortowanie po cenie i cenie za m² oraz mediany dzielnic. Stosowane po korekcie
 * rodzaju transakcji.
 */
public final class PricePlausibility {

    /** Poniżej tej kwoty cena jest symboliczna (dowolny typ i transakcja). */
    static final BigDecimal MIN_PRICE = BigDecimal.valueOf(100);
    /** Miesięczny najem mieszkania lub domu nie bywa tańszy — niższa kwota to cena za dobę lub za osobę. */
    static final BigDecimal MIN_MONTHLY_RENT = BigDecimal.valueOf(500);
    /** Najtańszy realny najem to ok. 20 zł/m² — poniżej 10 zł/m² cena dotyczy łóżka lub osoby, nie lokalu. */
    static final BigDecimal MIN_RENT_PER_M2 = BigDecimal.valueOf(10);

    private PricePlausibility() {
    }

    /** @return powód odrzucenia albo empty, gdy cena jest wiarygodna */
    public static Optional<String> rejectionReason(PropertyType type, TransactionType transaction, BigDecimal price, BigDecimal area) {
        if (price.compareTo(MIN_PRICE) < 0) {
            return Optional.of("symboliczna cena: " + price.toPlainString() + " zł");
        }
        boolean residential = type == PropertyType.APARTMENT || type == PropertyType.HOUSE;
        if (residential && transaction == TransactionType.RENT) {
            if (price.compareTo(MIN_MONTHLY_RENT) < 0) {
                return Optional.of("cena za dobę lub osobę zamiast najmu miesięcznego: " + price.toPlainString() + " zł");
            }
            if (area != null && area.signum() > 0 && price.divide(area, 2, RoundingMode.HALF_UP).compareTo(MIN_RENT_PER_M2) < 0) {
                return Optional.of("najem poniżej " + MIN_RENT_PER_M2 + " zł/m² (cena za osobę): " + price.toPlainString() + " zł");
            }
        }
        return Optional.empty();
    }
}
