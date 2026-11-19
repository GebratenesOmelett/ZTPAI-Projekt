package pl.domradar.ingestion.normalize;

import lombok.Builder;
import pl.domradar.property.MarketType;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Ogłoszenie po normalizacji: ceny w PLN, metraż w m², enumy, identyfikatory ze słowników, współrzędne. */
@Builder(toBuilder = true)
public record NormalizedListing(
        String externalId,
        String url,
        String title,
        String description,
        BigDecimal price,
        PropertyType propertyType,
        TransactionType transactionType,
        MarketType marketType,
        Long cityId,
        String cityName,
        Long districtId,
        String districtName,
        String street,
        double lat,
        double lon,
        boolean geocoded,
        BigDecimal area,
        Integer rooms,
        Integer floor,
        Integer totalFloors,
        Integer yearBuilt,
        Set<String> featureCodes,
        List<String> photos,
        String agencyName,
        boolean privateSeller
) {
}
