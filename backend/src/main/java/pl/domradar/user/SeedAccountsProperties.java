package pl.domradar.user;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Konta tworzone przy starcie aplikacji. Dane wyłącznie ze zmiennych środowiskowych (ADMIN_*, DEMO_USER_*). */
@ConfigurationProperties("app")
public record SeedAccountsProperties(Account admin, Account demoUser) {

    public record Account(String email, String password) {

        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
