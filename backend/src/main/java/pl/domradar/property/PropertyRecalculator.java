package pl.domradar.property;

import org.springframework.stereotype.Component;
import pl.domradar.common.util.TextNormalizer;
import pl.domradar.property.pricing.PriceAnalyzer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Przelicza pola zagregowane nieruchomości na podstawie jej ofert: cenę, cenę za m², obniżki,
 * liczbę portali, status (ACTIVE/ARCHIVED — reguła nr 3), tytuł, zdjęcie główne i tekst do wyszukiwania.
 */
@Component
public class PropertyRecalculator {

    public record Result(BigDecimal priceBefore, BigDecimal priceAfter, PropertyStatus statusBefore, PropertyStatus statusAfter) {

        public boolean priceDecreased() {
            return priceBefore != null && priceAfter != null && priceAfter.compareTo(priceBefore) < 0;
        }

        public boolean archived() {
            return statusBefore == PropertyStatus.ACTIVE && statusAfter == PropertyStatus.ARCHIVED;
        }
    }

    private final Clock clock;

    public PropertyRecalculator(Clock clock) {
        this.clock = clock;
    }

    public Result recalculate(Property property, List<Offer> offers) {
        BigDecimal priceBefore = property.getCurrentPrice();
        PropertyStatus statusBefore = property.getStatus();
        Instant now = clock.instant();

        List<PriceAnalyzer.OfferPrices> prices = offers.stream()
                .map(o -> new PriceAnalyzer.OfferPrices(o.isActive(), o.getPrice(),
                        o.getPriceHistory().stream().map(h -> new PriceAnalyzer.PricePoint(h.getPrice(), h.getChangedAt())).toList()))
                .toList();
        PriceAnalyzer.Summary summary = PriceAnalyzer.analyze(prices, property.getArea(), now);

        property.setCurrentPrice(summary.currentPrice());
        property.setPricePerM2(summary.pricePerM2());
        property.setPriceDropped(summary.priceDropped());
        property.setPriceDropPercent(summary.priceDropPercent());
        property.setLastPriceChangeAt(summary.lastPriceChangeAt());
        property.setLastPriceDropAt(summary.lastPriceDropAt());

        List<Offer> active = offers.stream().filter(Offer::isActive).toList();
        property.setActiveSourceCount((int) active.stream().map(o -> o.getSource().getId()).distinct().count());
        property.setStatus(active.isEmpty() ? PropertyStatus.ARCHIVED : PropertyStatus.ACTIVE);

        List<Offer> representativePool = active.isEmpty() ? offers : active;
        representativePool.stream().min(Comparator.comparing(Offer::getPrice)).ifPresent(cheapest -> {
            property.setTitle(cheapest.getTitle());
            property.setMainPhotoUrl(representativePool.stream()
                    .flatMap(o -> o.getPhotos().stream())
                    .findFirst().map(Photo::getUrl).orElse(property.getMainPhotoUrl()));
        });
        offers.stream().map(Offer::getLastSeenAt).filter(Objects::nonNull).max(Comparator.naturalOrder())
                .ifPresent(property::setLastSeenAt);
        offers.stream().map(Offer::getFirstSeenAt).filter(Objects::nonNull).min(Comparator.naturalOrder())
                .ifPresent(first -> {
                    if (property.getFirstSeenAt() == null || first.isBefore(property.getFirstSeenAt())) {
                        property.setFirstSeenAt(first);
                    }
                });
        property.setSearchText(buildSearchText(property, offers));
        return new Result(priceBefore, property.getCurrentPrice(), statusBefore, property.getStatus());
    }

    /** Tekst do wyszukiwania pełnotekstowego i trigramowego: lokalizacja + tytuły + opisy, znormalizowany. */
    static String buildSearchText(Property property, List<Offer> offers) {
        StringBuilder sb = new StringBuilder();
        if (property.getCity() != null) {
            sb.append(property.getCity().getName()).append(' ');
        }
        if (property.getDistrict() != null) {
            sb.append(property.getDistrict().getName()).append(' ');
        }
        if (property.getStreet() != null) {
            sb.append(property.getStreet()).append(' ');
        }
        for (Offer o : offers) {
            sb.append(o.getTitle()).append(' ');
        }
        for (Offer o : offers) {
            if (o.getDescription() != null) {
                sb.append(o.getDescription(), 0, Math.min(2000, o.getDescription().length())).append(' ');
            }
            if (sb.length() > 20_000) {
                break;
            }
        }
        String folded = TextNormalizer.fold(sb.toString());
        return folded.length() > 20_000 ? folded.substring(0, 20_000) : folded;
    }
}
