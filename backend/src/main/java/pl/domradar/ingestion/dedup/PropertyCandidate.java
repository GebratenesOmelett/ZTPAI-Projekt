package pl.domradar.ingestion.dedup;

import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;
import java.util.List;

/** Istniejąca nieruchomość rozważana jako duplikat nowego ogłoszenia (dane potrzebne do scoringu). */
public record PropertyCandidate(
        Long id,
        Long cityId,
        Long districtId,
        PropertyType propertyType,
        TransactionType transactionType,
        BigDecimal area,
        Integer rooms,
        Integer floor,
        Double lat,
        Double lon,
        BigDecimal currentPrice,
        List<String> titles
) {
}
