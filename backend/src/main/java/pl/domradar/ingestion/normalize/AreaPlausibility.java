package pl.domradar.ingestion.normalize;

import pl.domradar.property.PropertyType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Wyłapuje błędy wpisane przez ogłoszeniodawców w polu metrażu (np. 5158 m² zamiast 51,58 m² albo cena
 * wpisana jako metraż). Gdy metraż mieszkania lub domu przekracza realny zakres, używany jest metraż z tytułu
 * ("2 pokoje / 47 m²"); bez niego ogłoszenie jest odrzucane. Działki i lokale użytkowe nie są sprawdzane.
 */
public final class AreaPlausibility {

    static final BigDecimal MAX_APARTMENT_AREA = BigDecimal.valueOf(400);
    static final BigDecimal MAX_HOUSE_AREA = BigDecimal.valueOf(2000);
    private static final BigDecimal MIN_AREA = BigDecimal.valueOf(5);

    private AreaPlausibility() {
    }

    /** @return metraż do zapisania albo empty, gdy ogłoszenie należy odrzucić */
    public static Optional<BigDecimal> plausibleArea(PropertyType type, BigDecimal area, String title) {
        BigDecimal max = switch (type) {
            case APARTMENT, ROOM -> MAX_APARTMENT_AREA;
            case HOUSE -> MAX_HOUSE_AREA;
            default -> null;
        };
        if (max == null || area.compareTo(max) <= 0) {
            return Optional.of(area);
        }
        return ValueParsers.areaInText(title)
                .filter(a -> a.compareTo(MIN_AREA) >= 0 && a.compareTo(max) <= 0)
                .map(a -> a.setScale(2, RoundingMode.HALF_UP));
    }
}
