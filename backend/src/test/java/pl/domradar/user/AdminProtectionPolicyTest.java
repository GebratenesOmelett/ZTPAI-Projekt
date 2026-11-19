package pl.domradar.user;

import org.junit.jupiter.api.Test;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reguła biznesowa nr 6 — ochrona własnego konta i ostatniego administratora. */
class AdminProtectionPolicyTest {

    private static User user(long id, Role role, boolean enabled) {
        User u = new User("u" + id + "@test.pl", "x", "U", role);
        u.setId(id);
        u.setEnabled(enabled);
        return u;
    }

    private static void expect(ErrorCode code, Runnable r) {
        assertThatThrownBy(r::run).isInstanceOf(DomainException.class)
                .satisfies(e -> org.assertj.core.api.Assertions.assertThat(((DomainException) e).getCode()).isEqualTo(code));
    }

    @Test
    void adminCannotDemoteBlockOrDeleteHimself() {
        User me = user(1, Role.ADMIN, true);

        expect(ErrorCode.SELF_MODIFICATION, () -> AdminProtectionPolicy.check(1L, me, Role.USER, null, false, 5));
        expect(ErrorCode.SELF_MODIFICATION, () -> AdminProtectionPolicy.check(1L, me, null, false, false, 5));
        expect(ErrorCode.SELF_MODIFICATION, () -> AdminProtectionPolicy.check(1L, me, null, null, true, 5));
    }

    @Test
    void lastActiveAdminCannotBeDemotedBlockedOrDeleted() {
        User other = user(2, Role.ADMIN, true);

        expect(ErrorCode.LAST_ADMIN, () -> AdminProtectionPolicy.check(1L, other, Role.USER, null, false, 1));
        expect(ErrorCode.LAST_ADMIN, () -> AdminProtectionPolicy.check(1L, other, null, false, false, 1));
        expect(ErrorCode.LAST_ADMIN, () -> AdminProtectionPolicy.check(1L, other, null, null, true, 1));
    }

    @Test
    void anotherAdminCanBeDemotedWhenOthersRemain() {
        assertThatCode(() -> AdminProtectionPolicy.check(1L, user(2, Role.ADMIN, true), Role.USER, null, false, 2))
                .doesNotThrowAnyException();
    }

    @Test
    void regularUsersAndNoOpChangesAreNotRestricted() {
        assertThatCode(() -> AdminProtectionPolicy.check(1L, user(3, Role.USER, true), null, false, false, 1)).doesNotThrowAnyException();
        assertThatCode(() -> AdminProtectionPolicy.check(1L, user(3, Role.USER, true), null, null, true, 1)).doesNotThrowAnyException();
        assertThatCode(() -> AdminProtectionPolicy.check(1L, user(1, Role.ADMIN, true), Role.ADMIN, true, false, 1)).doesNotThrowAnyException();
        assertThatCode(() -> AdminProtectionPolicy.check(1L, user(2, Role.ADMIN, false), Role.USER, null, false, 1)).doesNotThrowAnyException();
    }
}
