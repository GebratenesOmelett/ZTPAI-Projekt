package pl.domradar.user;

import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;

import java.util.Map;

/**
 * Reguła biznesowa nr 6: administrator nie może zdegradować, zablokować ani usunąć siebie,
 * ani ostatniego aktywnego administratora w systemie (ochrona przed utratą dostępu do panelu).
 */
public final class AdminProtectionPolicy {

    private AdminProtectionPolicy() {
    }

    /**
     * @param actorId            id administratora wykonującego operację
     * @param target             modyfikowany użytkownik
     * @param newRole            docelowa rola (null = bez zmian)
     * @param newEnabled         docelowy status (null = bez zmian)
     * @param deleting           czy użytkownik ma zostać usunięty
     * @param activeAdminCount   liczba aktywnych (enabled) administratorów
     */
    public static void check(Long actorId, User target, Role newRole, Boolean newEnabled, boolean deleting, long activeAdminCount) {
        boolean losesAdmin = target.getRole() == Role.ADMIN && target.isEnabled()
                && (deleting || (newRole != null && newRole != Role.ADMIN) || Boolean.FALSE.equals(newEnabled));
        if (!losesAdmin && !(deleting && target.getId().equals(actorId))) {
            return;
        }
        if (target.getId().equals(actorId)) {
            throw new DomainException(ErrorCode.SELF_MODIFICATION,
                    "Nie możesz odebrać sobie uprawnień administratora, zablokować ani usunąć własnego konta",
                    Map.of("userId", target.getId()));
        }
        if (activeAdminCount <= 1) {
            throw new DomainException(ErrorCode.LAST_ADMIN,
                    "Nie można zdegradować, zablokować ani usunąć ostatniego aktywnego administratora",
                    Map.of("userId", target.getId()));
        }
    }
}
