package pl.domradar.auth;

import org.junit.jupiter.api.Test;
import pl.domradar.user.Role;
import pl.domradar.user.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final JwtProperties PROPS = new JwtProperties(
            "unit-test-secret-unit-test-secret-unit-test-secret", "domradar",
            Duration.ofMinutes(15), Duration.ofDays(14), "domradar_refresh", false);

    private static User user() {
        User u = new User("jan@example.com", "hash", "Jan", Role.ADMIN);
        u.setId(42L);
        return u;
    }

    @Test
    void createdTokenCanBeParsedBack() {
        JwtService service = new JwtService(PROPS);
        String token = service.createAccessToken(user());

        assertThat(service.parse(token)).hasValueSatisfying(cu -> {
            assertThat(cu.id()).isEqualTo(42L);
            assertThat(cu.email()).isEqualTo("jan@example.com");
            assertThat(cu.role()).isEqualTo(Role.ADMIN);
        });
    }

    @Test
    void expiredTokenIsRejected() {
        Instant issued = Instant.parse("2026-01-01T10:00:00Z");
        String token = new JwtService(PROPS, Clock.fixed(issued, ZoneOffset.UTC)).createAccessToken(user());
        JwtService later = new JwtService(PROPS, Clock.fixed(issued.plus(Duration.ofMinutes(16)), ZoneOffset.UTC));

        assertThat(later.parse(token)).isEmpty();
    }

    @Test
    void tokenSignedWithDifferentSecretIsRejected() {
        String token = new JwtService(PROPS).createAccessToken(user());
        JwtProperties other = new JwtProperties("another-secret-another-secret-another-secret", "domradar",
                Duration.ofMinutes(15), Duration.ofDays(14), "domradar_refresh", false);

        assertThat(new JwtService(other).parse(token)).isEmpty();
    }
}
