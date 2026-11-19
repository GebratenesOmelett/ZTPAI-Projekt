package pl.domradar.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.domradar.auth.dto.AuthResponse;
import pl.domradar.auth.dto.LoginRequest;
import pl.domradar.auth.dto.RegisterRequest;
import pl.domradar.user.dto.UserDto;

import java.net.URI;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Rejestracja, logowanie, odświeżanie tokenu (refresh token w ciasteczku httpOnly)")
public class AuthController {

    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final JwtProperties jwtProperties;

    @PostMapping("/register")
    @Operation(summary = "Rejestracja nowego użytkownika (rola USER) z automatycznym zalogowaniem")
    @ApiResponse(responseCode = "201", description = "Konto utworzone")
    @ApiResponse(responseCode = "400", description = "Błędy walidacji pól")
    @ApiResponse(responseCode = "409", description = "E-mail zajęty (EMAIL_TAKEN)")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResult result = authService.register(request.email(), request.password(), request.displayName());
        return withRefreshCookie(ResponseEntity.created(URI.create("/api/v1/auth/me")), result);
    }

    @PostMapping("/login")
    @Operation(summary = "Logowanie — zwraca access token, refresh token trafia do ciasteczka httpOnly")
    @ApiResponse(responseCode = "200", description = "Zalogowano")
    @ApiResponse(responseCode = "401", description = "Niepoprawne dane logowania (INVALID_CREDENTIALS)")
    @ApiResponse(responseCode = "403", description = "Konto zablokowane (ACCOUNT_DISABLED)")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withRefreshCookie(ResponseEntity.ok(), authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Wydanie nowego access tokenu na podstawie refresh tokenu z ciasteczka (rotacja)")
    @ApiResponse(responseCode = "200", description = "Nowa para tokenów")
    @ApiResponse(responseCode = "401", description = "Brak lub nieważny refresh token (INVALID_REFRESH_TOKEN)")
    public ResponseEntity<AuthResponse> refresh(
            @Parameter(hidden = true) @CookieValue(name = "${app.jwt.refresh-cookie-name}", required = false) String refreshToken) {
        return withRefreshCookie(ResponseEntity.ok(), authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    @Operation(summary = "Wylogowanie — unieważnia refresh token i czyści ciasteczko")
    @ApiResponse(responseCode = "204", description = "Wylogowano")
    public ResponseEntity<Void> logout(
            @Parameter(hidden = true) @CookieValue(name = "${app.jwt.refresh-cookie-name}", required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
                .build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Dane zalogowanego użytkownika")
    @ApiResponse(responseCode = "200", description = "Zalogowany użytkownik")
    @ApiResponse(responseCode = "401", description = "Brak tokenu")
    public UserDto me(@AuthenticationPrincipal CurrentUser currentUser) {
        return authService.me(currentUser.id());
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(ResponseEntity.BodyBuilder builder, AuthResult result) {
        return builder
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(), result.refreshTokenTtl()).toString())
                .body(new AuthResponse(result.accessToken(), "Bearer", result.accessTokenExpiresIn(), result.user()));
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(jwtProperties.refreshCookieName(), value)
                .httpOnly(true)
                .secure(jwtProperties.cookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
