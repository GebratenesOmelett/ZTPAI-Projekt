package pl.domradar.ingestion.normalize;

import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * Koryguje rodzaj transakcji ogłoszeń wystawionych w złej kategorii (np. "sprzedaż" za 2500 zł albo "wynajem"
 * za 800 000 zł). Decyduje cena za m², bo zakresy najmu i sprzedaży mieszkań się nie pokrywają:
 * miesięczny czynsz to ok. 30–400 zł/m² (najdroższe apartamenty), a cena sprzedaży zaczyna się od kilkuset zł/m²
 * (udziały, lokale do remontu) i zwykle wynosi 5–30 tys. zł/m². Wartości pośrednie nie są zmieniane.
 * Dotyczy tylko mieszkań i domów — działki czy lokale użytkowe mają inne zakresy cen.
 */
public final class TransactionClassifier {

    /** "Sprzedaż" tańsza niż tyle zł/m² to w praktyce miesięczny czynsz. */
    static final BigDecimal MAX_RENT_LIKE_SALE_PER_M2 = BigDecimal.valueOf(250);
    /** "Wynajem" droższy niż tyle zł/m² to w praktyce cena sprzedaży. */
    static final BigDecimal MIN_SALE_LIKE_RENT_PER_M2 = BigDecimal.valueOf(1000);

    private static final Set<PropertyType> RESIDENTIAL = Set.of(PropertyType.APARTMENT, PropertyType.HOUSE);

    private TransactionClassifier() {
    }

    public static TransactionType classify(TransactionType declared, PropertyType type, BigDecimal price, BigDecimal area) {
        if (!RESIDENTIAL.contains(type) || price == null || area == null || area.signum() <= 0) {
            return declared;
        }
        BigDecimal perM2 = price.divide(area, 2, RoundingMode.HALF_UP);
        if (declared == TransactionType.SALE && perM2.compareTo(MAX_RENT_LIKE_SALE_PER_M2) < 0) {
            return TransactionType.RENT;
        }
        if (declared == TransactionType.RENT && perM2.compareTo(MIN_SALE_LIKE_RENT_PER_M2) > 0) {
            return TransactionType.SALE;
        }
        return declared;
    }
}
