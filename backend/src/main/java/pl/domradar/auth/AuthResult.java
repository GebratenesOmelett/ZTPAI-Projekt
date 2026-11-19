package pl.domradar.auth;

import pl.domradar.user.dto.UserDto;

import java.time.Duration;

/** Wynik operacji uwierzytelnienia w warstwie serwisów (bez wiedzy o ciasteczkach i HTTP). */
public record AuthResult(String accessToken, long accessTokenExpiresIn, String refreshToken,
                         Duration refreshTokenTtl, UserDto user) {
}
