package pl.domradar.ingestion.adapter;

import lombok.Builder;

import java.util.List;

/**
 * Surowe ogłoszenie w postaci zbliżonej do tej, w jakiej publikuje je portal
 * (teksty typu "649 000 zł", "54,3 m²", "parter/4"). Normalizacja odbywa się później
 * w {@code ListingNormalizer}. Rekord jest serializowany do JSON w wiadomościach RabbitMQ.
 */
@Builder(toBuilder = true)
public record RawListing(
        String externalId,
        String url,
        String title,
        String description,
        String priceText,
        String currency,
        String areaText,
        String roomsText,
        String floorText,
        String totalFloorsText,
        String yearBuiltText,
        String propertyTypeText,
        String transactionText,
        String marketText,
        String city,
        String district,
        String street,
        Double lat,
        Double lon,
        List<String> features,
        List<String> photos,
        String agencyName,
        String sellerText
) {
}
