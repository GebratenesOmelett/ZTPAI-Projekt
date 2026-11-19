package pl.domradar.property.event;

import java.math.BigDecimal;

/** Aktualna (najniższa) cena nieruchomości spadła. */
public record PriceDroppedEvent(Long propertyId, Long importJobId, BigDecimal oldPrice, BigDecimal newPrice) {
}
