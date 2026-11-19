package pl.domradar.property.pricing;

import org.junit.jupiter.api.Test;
import pl.domradar.property.pricing.PriceAnalyzer.OfferPrices;
import pl.domradar.property.pricing.PriceAnalyzer.PricePoint;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriceAnalyzerTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final BigDecimal AREA = new BigDecimal("50");

    private static Instant daysAgo(int days) {
        return NOW.minus(Duration.ofDays(days));
    }

    private static PricePoint p(String price, Instant at) {
        return new PricePoint(new BigDecimal(price), at);
    }

    private static OfferPrices offer(boolean active, String current, PricePoint... history) {
        return new OfferPrices(active, new BigDecimal(current), List.of(history));
    }

    @Test
    void currentPriceIsLowestActiveOfferAndPricePerM2IsComputed() {
        PriceAnalyzer.Summary s = PriceAnalyzer.analyze(List.of(
                offer(true, "800000", p("800000", daysAgo(10))),
                offer(true, "780000", p("780000", daysAgo(5))),
                offer(false, "700000", p("700000", daysAgo(50)))), AREA, NOW);

        assertThat(s.currentPrice()).isEqualByComparingTo("780000");
        assertThat(s.pricePerM2()).isEqualByComparingTo("15600.00");
    }

    @Test
    void dropOfAtLeastThreePercentWithinThirtyDaysSetsPriceDropped() {
        PriceAnalyzer.Summary s = PriceAnalyzer.analyze(List.of(
                offer(true, "960000", p("1000000", daysAgo(40)), p("960000", daysAgo(3)))), AREA, NOW);

        assertThat(s.priceDropped()).isTrue();
        assertThat(s.priceDropPercent()).isEqualByComparingTo("4.00");
        assertThat(s.lastPriceDropAt()).isEqualTo(daysAgo(3));
        assertThat(s.lastPriceChangeAt()).isEqualTo(daysAgo(3));
    }

    @Test
    void smallDropBelowThresholdDoesNotSetPriceDropped() {
        PriceAnalyzer.Summary s = PriceAnalyzer.analyze(List.of(
                offer(true, "980000", p("1000000", daysAgo(20)), p("980000", daysAgo(2)))), AREA, NOW);

        assertThat(s.priceDropped()).isFalse();
        assertThat(s.priceDropPercent()).isEqualByComparingTo("2.00");
    }

    @Test
    void dropOlderThanThirtyDaysDoesNotCountAsRecent() {
        PriceAnalyzer.Summary s = PriceAnalyzer.analyze(List.of(
                offer(true, "900000", p("1000000", daysAgo(90)), p("900000", daysAgo(45)))), AREA, NOW);

        assertThat(s.priceDropped()).isFalse();
        assertThat(s.priceDropPercent()).isEqualByComparingTo("10.00");
    }

    @Test
    void priceIncreaseIsAChangeButNotADrop() {
        PriceAnalyzer.Summary s = PriceAnalyzer.analyze(List.of(
                offer(true, "1050000", p("1000000", daysAgo(20)), p("1050000", daysAgo(1)))), AREA, NOW);

        assertThat(s.priceDropped()).isFalse();
        assertThat(s.lastPriceChangeAt()).isEqualTo(daysAgo(1));
        assertThat(s.lastPriceDropAt()).isNull();
    }

    @Test
    void withoutActiveOffersLastKnownLowestPriceIsKept() {
        PriceAnalyzer.Summary s = PriceAnalyzer.analyze(List.of(
                offer(false, "510000", p("510000", daysAgo(60))),
                offer(false, "500000", p("500000", daysAgo(60)))), AREA, NOW);

        assertThat(s.currentPrice()).isEqualByComparingTo("500000");
        assertThat(s.priceDropped()).isFalse();
    }
}
