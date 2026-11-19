package pl.domradar.property;

import java.math.BigDecimal;
import java.util.List;

/**
 * Znormalizowane atrybuty nieruchomości tak, jak podał je konkretny portal.
 * Przechowywane w JSONB przy ofercie — pozwalają rozdzielić (split) błędnie scaloną nieruchomość
 * i ponownie policzyć scoring deduplikacji.
 */
public record OfferAttributes(
        PropertyType propertyType,
        TransactionType transactionType,
        MarketType marketType,
        Long cityId,
        Long districtId,
        String street,
        Double lat,
        Double lon,
        BigDecimal area,
        Integer rooms,
        Integer floor,
        Integer totalFloors,
        Integer yearBuilt,
        List<String> features
) {
}
