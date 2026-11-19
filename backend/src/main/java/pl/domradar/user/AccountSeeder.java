package pl.domradar.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** Tworzy konto administratora (i opcjonalnie konto demo) przy starcie, jeśli jeszcze nie istnieją. */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class AccountSeeder implements ApplicationRunner {

    private final SeedAccountsProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed(properties.admin(), Role.ADMIN, "Administrator");
        seed(properties.demoUser(), Role.USER, "Użytkownik demo");
    }

    private void seed(SeedAccountsProperties.Account account, Role role, String displayName) {
        if (account == null || !account.isConfigured()) {
            if (role == Role.ADMIN) {
                log.warn("ADMIN_EMAIL / ADMIN_PASSWORD not set - admin account will not be created");
            }
            return;
        }
        String email = account.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        userRepository.save(new User(email, passwordEncoder.encode(account.password()), displayName, role));
        log.info("Created {} account {}", role, email);
    }
}
