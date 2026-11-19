package pl.domradar.property.lifecycle;

import org.junit.jupiter.api.Test;
import pl.domradar.property.Offer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OfferLifecyclePolicyTest {

    private static final int THRESHOLD = 3;

    @Test
    void offerBecomesInactiveAfterThreeMissedSuccessfulImports() {
        Offer offer = new Offer();
        offer.setActive(true);

        assertThat(OfferLifecyclePolicy.registerMiss(offer, THRESHOLD)).isFalse();
        assertThat(OfferLifecyclePolicy.registerMiss(offer, THRESHOLD)).isFalse();
        assertThat(offer.isActive()).isTrue();
        assertThat(OfferLifecyclePolicy.registerMiss(offer, THRESHOLD)).isTrue();
        assertThat(offer.isActive()).isFalse();
        assertThat(offer.getMissedImports()).isEqualTo(3);
        // kolejne pominięcie nie "dezaktywuje" ponownie
        assertThat(OfferLifecyclePolicy.registerMiss(offer, THRESHOLD)).isFalse();
    }

    @Test
    void seeingOfferAgainResetsCounterAndReactivates() {
        Offer offer = new Offer();
        offer.setActive(false);
        offer.setMissedImports(5);
        Instant now = Instant.parse("2026-11-19T10:00:00Z");

        OfferLifecyclePolicy.markSeen(offer, 42L, now);

        assertThat(offer.isActive()).isTrue();
        assertThat(offer.getMissedImports()).isZero();
        assertThat(offer.getLastSeenJobId()).isEqualTo(42L);
        assertThat(offer.getLastSeenAt()).isEqualTo(now);
    }

    @Test
    void missCounterIsResetBetweenGaps() {
        Offer offer = new Offer();
        offer.setActive(true);
        OfferLifecyclePolicy.registerMiss(offer, THRESHOLD);
        OfferLifecyclePolicy.registerMiss(offer, THRESHOLD);
        OfferLifecyclePolicy.markSeen(offer, 1L, Instant.now());
        OfferLifecyclePolicy.registerMiss(offer, THRESHOLD);

        assertThat(offer.isActive()).isTrue();
        assertThat(offer.getMissedImports()).isEqualTo(1);
    }
}
