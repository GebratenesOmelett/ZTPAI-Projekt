package pl.domradar.property.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Reguła biznesowa nr 2 — analiza cen nieruchomości na podstawie ofert i ich historii.
 * <ul>
 *   <li>{@code currentPrice} = najniższa cena aktywnej oferty (gdy brak aktywnych — najniższa ostatnia cena),</li>
 *   <li>{@code priceDropped} = któraś aktywna oferta potaniała o ≥ 3% względem swojej najwyższej ceny z ostatnich 30 dni,</li>
 *   <li>{@code priceDropPercent} = największa obniżka aktywnej oferty względem jej najwyższej ceny w historii,</li>
 *   <li>{@code lastPriceChangeAt}/{@code lastPriceDropAt} = czas ostatniej zmiany / obniżki ceny aktywnej oferty.</li>
 * </ul>
 * Czysta funkcja — bez zależności od JPA i zegara systemowego (testowana jednostkowo).
 */
public final class PriceAnalyzer {

    public static final Duration DROP_WINDOW = Duration.ofDays(30);
    public static final BigDecimal DROP_THRESHOLD_PERCENT = new BigDecimal("3");

    public record PricePoint(BigDecimal price, Instant at) {
    }

    /** Oferta z historią cen posortowaną rosnąco po czasie (pierwszy wpis = cena początkowa). */
    public record OfferPrices(boolean active, BigDecimal price, List<PricePoint> history) {
    }

    public record Summary(BigDecimal currentPrice, BigDecimal pricePerM2, boolean priceDropped,
                          BigDecimal priceDropPercent, Instant lastPriceChangeAt, Instant lastPriceDropAt) {
    }

    private PriceAnalyzer() {
    }

    public static Summary analyze(List<OfferPrices> offers, BigDecimal area, Instant now) {
        List<OfferPrices> active = offers.stream().filter(OfferPrices::active).toList();
        List<OfferPrices> basis = active.isEmpty() ? offers : active;

        BigDecimal current = basis.stream().map(OfferPrices::price).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        BigDecimal perM2 = current == null || area == null || area.signum() <= 0 ? null
                : current.divide(area, 2, RoundingMode.HALF_UP);

        Instant windowStart = now.minus(DROP_WINDOW);
        boolean dropped = false;
        BigDecimal maxDrop = BigDecimal.ZERO;
        Instant lastChange = null;
        Instant lastDrop = null;

        for (OfferPrices offer : active) {
            List<PricePoint> history = offer.history().stream().sorted(Comparator.comparing(PricePoint::at)).toList();
            if (offer.price() == null || history.isEmpty()) {
                continue;
            }
            BigDecimal maxInWindow = maxPriceSince(history, windowStart);
            if (dropPercent(maxInWindow, offer.price()).compareTo(DROP_THRESHOLD_PERCENT) >= 0) {
                dropped = true;
            }
            BigDecimal maxEver = history.stream().map(PricePoint::price).max(Comparator.naturalOrder()).orElse(offer.price());
            maxDrop = maxDrop.max(dropPercent(maxEver, offer.price()));

            for (int i = 1; i < history.size(); i++) {
                PricePoint prev = history.get(i - 1);
                PricePoint point = history.get(i);
                if (point.price().compareTo(prev.price()) != 0) {
                    lastChange = latest(lastChange, point.at());
                }
                if (point.price().compareTo(prev.price()) < 0) {
                    lastDrop = latest(lastDrop, point.at());
                }
            }
        }
        return new Summary(current, perM2, dropped, maxDrop.setScale(2, RoundingMode.HALF_UP), lastChange, lastDrop);
    }

    /** Najwyższa cena obowiązująca w oknie: cena sprzed początku okna (jeśli była) oraz wszystkie zmiany w oknie. */
    static BigDecimal maxPriceSince(List<PricePoint> history, Instant windowStart) {
        BigDecimal max = null;
        BigDecimal priceAtWindowStart = null;
        for (PricePoint p : history) {
            if (p.at().isBefore(windowStart)) {
                priceAtWindowStart = p.price();
            } else {
                max = max == null ? p.price() : max.max(p.price());
            }
        }
        if (priceAtWindowStart != null) {
            max = max == null ? priceAtWindowStart : max.max(priceAtWindowStart);
        }
        return max;
    }

    static BigDecimal dropPercent(BigDecimal reference, BigDecimal current) {
        if (reference == null || current == null || reference.signum() <= 0 || current.compareTo(reference) >= 0) {
            return BigDecimal.ZERO;
        }
        return reference.subtract(current).multiply(BigDecimal.valueOf(100)).divide(reference, 4, RoundingMode.HALF_UP);
    }

    private static Instant latest(Instant a, Instant b) {
        return a == null || b.isAfter(a) ? b : a;
    }
}
