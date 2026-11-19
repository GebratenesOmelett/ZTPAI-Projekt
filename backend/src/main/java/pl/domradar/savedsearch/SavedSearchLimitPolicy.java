package pl.domradar.savedsearch;

import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.user.Role;

import java.util.Map;

/**
 * Reguła biznesowa nr 5: użytkownik z rolą USER może mieć maksymalnie N zapisanych wyszukiwań,
 * w tym maksymalnie M z alertem INSTANT. Administratorów limit nie dotyczy.
 */
public final class SavedSearchLimitPolicy {

    private SavedSearchLimitPolicy() {
    }

    /**
     * @param existingSearches       liczba istniejących wyszukiwań (bez modyfikowanego)
     * @param existingInstantAlerts  liczba istniejących alertów INSTANT (bez modyfikowanego)
     * @param creating               czy tworzymy nowe wyszukiwanie
     * @param wantsInstant           czy wynikowe wyszukiwanie ma aktywny alert INSTANT
     */
    public static void check(Role role, LimitsProperties limits, long existingSearches, long existingInstantAlerts,
                             boolean creating, boolean wantsInstant) {
        if (role == Role.ADMIN) {
            return;
        }
        if (creating && existingSearches >= limits.maxSavedSearches()) {
            throw new DomainException(ErrorCode.SAVED_SEARCH_LIMIT,
                    "Osiągnięto limit " + limits.maxSavedSearches() + " zapisanych wyszukiwań",
                    Map.of("limit", limits.maxSavedSearches()));
        }
        if (wantsInstant && existingInstantAlerts >= limits.maxInstantAlerts()) {
            throw new DomainException(ErrorCode.INSTANT_ALERT_LIMIT,
                    "Można mieć maksymalnie " + limits.maxInstantAlerts() + " alerty natychmiastowe (INSTANT)",
                    Map.of("limit", limits.maxInstantAlerts()));
        }
    }
}
