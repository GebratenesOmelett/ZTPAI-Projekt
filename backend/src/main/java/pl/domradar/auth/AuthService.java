package pl.domradar.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.user.Role;
import pl.domradar.user.User;
import pl.domradar.user.UserMapper;
import pl.domradar.user.UserRepository;
import pl.domradar.user.dto.UserDto;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Rejestracja, logowanie i obsługa refresh tokenów.
 * Refresh tokeny są rotowane przy każdym użyciu; ponowne użycie unieważnionego tokenu
 * (oznaka kradzieży) unieważnia wszystkie sesje użytkownika.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserMapper userMapper;

    @Transactional
    public AuthResult register(String email, String rawPassword, String displayName) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw emailTaken();
        }
        User user = new User(normalizedEmail, passwordEncoder.encode(rawPassword), displayName.trim(), Role.USER);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw emailTaken();
        }
        log.info("Registered user {}", user.getId());
        return issueTokens(user);
    }

    @Transactional
    public AuthResult login(String email, String rawPassword) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(email))
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()))
                .orElseThrow(() -> new DomainException(ErrorCode.INVALID_CREDENTIALS, "Niepoprawny e-mail lub hasło"));
        ensureEnabled(user);
        return issueTokens(user);
    }

    @Transactional(noRollbackFor = DomainException.class)
    public AuthResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw invalidRefreshToken();
        }
        RefreshToken token = refreshTokenRepository.findByTokenHash(TokenHasher.sha256(rawRefreshToken))
                .orElseThrow(AuthService::invalidRefreshToken);
        Instant now = Instant.now();
        if (token.getRevokedAt() != null) {
            log.warn("Reuse of revoked refresh token detected for user {} - revoking all sessions", token.getUser().getId());
            refreshTokenRepository.revokeAllForUser(token.getUser().getId(), now);
            throw invalidRefreshToken();
        }
        if (!token.isUsable(now)) {
            throw invalidRefreshToken();
        }
        User user = token.getUser();
        ensureEnabled(user);
        token.setRevokedAt(now);
        return issueTokens(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(TokenHasher.sha256(rawRefreshToken))
                .filter(t -> t.getRevokedAt() == null)
                .ifPresent(t -> t.setRevokedAt(Instant.now()));
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return userRepository.findById(userId)
                .map(userMapper::toDto)
                .orElseThrow(() -> DomainException.notFound("Użytkownik", userId));
    }

    /** Sprzątanie wygasłych refresh tokenów raz na dobę. */
    @Scheduled(cron = "0 30 4 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        int removed = refreshTokenRepository.deleteExpired(Instant.now().minus(1, ChronoUnit.DAYS));
        if (removed > 0) {
            log.info("Purged {} expired refresh tokens", removed);
        }
    }

    private AuthResult issueTokens(User user) {
        String refresh = TokenHasher.newToken();
        refreshTokenRepository.save(new RefreshToken(user, TokenHasher.sha256(refresh),
                Instant.now().plus(jwtProperties.refreshTokenTtl())));
        return new AuthResult(jwtService.createAccessToken(user), jwtService.accessTokenTtlSeconds(),
                refresh, jwtProperties.refreshTokenTtl(), userMapper.toDto(user));
    }

    private static void ensureEnabled(User user) {
        if (!user.isEnabled()) {
            throw new DomainException(ErrorCode.ACCOUNT_DISABLED, "Konto zostało zablokowane przez administratora");
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static DomainException emailTaken() {
        return new DomainException(ErrorCode.EMAIL_TAKEN, "Konto z tym adresem e-mail już istnieje");
    }

    private static DomainException invalidRefreshToken() {
        return new DomainException(ErrorCode.INVALID_REFRESH_TOKEN, "Sesja wygasła lub jest nieprawidłowa — zaloguj się ponownie");
    }
}
