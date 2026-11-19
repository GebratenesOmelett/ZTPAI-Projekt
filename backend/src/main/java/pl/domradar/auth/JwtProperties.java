package pl.domradar.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Konfiguracja tokenów. Sekret pochodzi wyłącznie ze zmiennej środowiskowej JWT_SECRET. */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "JWT_SECRET musi mieć co najmniej 32 znaki") String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl,
        @NotBlank String refreshCookieName,
        boolean cookieSecure
) {
}
