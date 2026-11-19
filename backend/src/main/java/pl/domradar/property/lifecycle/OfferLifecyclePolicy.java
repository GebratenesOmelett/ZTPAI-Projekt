package pl.domradar.property.lifecycle;

import pl.domradar.property.Offer;

import java.time.Instant;

/**
 * Reguła biznesowa nr 3 (część dotycząca ofert): oferta niewidziana w N kolejnych udanych importach
 * swojego źródła staje się nieaktywna. Każde ponowne pojawienie się zeruje licznik i reaktywuje ofertę.
 */
public final class OfferLifecyclePolicy {

    private OfferLifecyclePolicy() {
    }

    /** Oferta pojawiła się w imporcie. */
    public static void markSeen(Offer offer, Long jobId, Instant now) {
        offer.setMissedImports(0);
        offer.setActive(true);
        offer.setLastSeenAt(now);
        offer.setLastSeenJobId(jobId);
    }

    /**
     * Wariant dla importów częściowych (adaptery live przeglądają tylko najnowsze strony wyników):
     * oferta wygasa, gdy nie była widziana od {@code staleBefore}.
     *
     * @return true, jeśli w wyniku tej operacji oferta została dezaktywowana
     */
    public static boolean expireIfStale(Offer offer, Instant staleBefore) {
        if (offer.isActive() && offer.getLastSeenAt() != null && offer.getLastSeenAt().isBefore(staleBefore)) {
            offer.setActive(false);
            return true;
        }
        return false;
    }

    /**
     * Oferta nie pojawiła się w zakończonym sukcesem imporcie swojego źródła.
     *
     * @return true, jeśli w wyniku tej operacji oferta została dezaktywowana
     */
    public static boolean registerMiss(Offer offer, int threshold) {
        offer.setMissedImports(offer.getMissedImports() + 1);
        if (offer.isActive() && offer.getMissedImports() >= threshold) {
            offer.setActive(false);
            return true;
        }
        return false;
    }
}
