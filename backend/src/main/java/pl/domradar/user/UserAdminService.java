package pl.domradar.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.auth.RefreshTokenRepository;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.web.PageResponse;
import pl.domradar.user.dto.UserDto;
import pl.domradar.user.dto.UserUpdateRequest;

import java.time.Instant;

/** Zarządzanie użytkownikami przez administratora z ochroną ostatniego admina (reguła nr 6). */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<UserDto> list(String q, Role role, Pageable pageable) {
        return PageResponse.of(userRepository.search(q == null || q.isBlank() ? null : q.trim(), role, pageable), mapper::toDto);
    }

    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return mapper.toDto(find(id));
    }

    @Transactional
    public UserDto update(Long actorId, Long id, UserUpdateRequest request) {
        User target = userRepository.findByIdForUpdate(id).orElseThrow(() -> DomainException.notFound("Użytkownik", id));
        AdminProtectionPolicy.check(actorId, target, request.role(), request.enabled(), false, activeAdmins());
        if (request.role() != null) {
            target.setRole(request.role());
        }
        if (request.enabled() != null) {
            target.setEnabled(request.enabled());
            if (!request.enabled()) {
                refreshTokenRepository.revokeAllForUser(id, Instant.now());
            }
        }
        log.info("User {} updated by admin {}: role={}, enabled={}", id, actorId, target.getRole(), target.isEnabled());
        return mapper.toDto(target);
    }

    @Transactional
    public void delete(Long actorId, Long id) {
        User target = userRepository.findByIdForUpdate(id).orElseThrow(() -> DomainException.notFound("Użytkownik", id));
        AdminProtectionPolicy.check(actorId, target, null, null, true, activeAdmins());
        userRepository.delete(target);
        log.info("User {} deleted by admin {}", id, actorId);
    }

    private long activeAdmins() {
        return userRepository.countByRoleAndEnabledTrue(Role.ADMIN);
    }

    private User find(Long id) {
        return userRepository.findById(id).orElseThrow(() -> DomainException.notFound("Użytkownik", id));
    }
}
