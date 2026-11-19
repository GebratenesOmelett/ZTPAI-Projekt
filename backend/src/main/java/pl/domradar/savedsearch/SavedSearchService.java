package pl.domradar.savedsearch;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.common.exception.DomainException;
import pl.domradar.savedsearch.dto.SavedSearchDto;
import pl.domradar.savedsearch.dto.SavedSearchPatchRequest;
import pl.domradar.savedsearch.dto.SavedSearchRequest;
import pl.domradar.user.User;
import pl.domradar.user.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Zapisane wyszukiwania użytkownika z limitami (reguła nr 5). Wiersz użytkownika jest blokowany
 * (SELECT ... FOR UPDATE), aby dwa równoległe żądania nie ominęły limitu.
 */
@Service
@RequiredArgsConstructor
public class SavedSearchService {

    private final SavedSearchRepository repository;
    private final UserRepository userRepository;
    private final SavedSearchMapper mapper;
    private final LimitsProperties limits;

    @Transactional(readOnly = true)
    public List<SavedSearchDto> list(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SavedSearchDto get(Long userId, Long id) {
        return mapper.toDto(find(userId, id));
    }

    @Transactional
    public SavedSearchDto create(Long userId, SavedSearchRequest request) {
        User user = userRepository.findByIdForUpdate(userId).orElseThrow(() -> DomainException.notFound("Użytkownik", userId));
        AlertMode mode = request.alertMode() == null ? AlertMode.DAILY : request.alertMode();
        boolean wantsInstant = request.alertEnabled() && mode == AlertMode.INSTANT;
        SavedSearchLimitPolicy.check(user.getRole(), limits, repository.countByUserId(userId),
                repository.countByUserIdAndAlertEnabledTrueAndAlertMode(userId, AlertMode.INSTANT), true, wantsInstant);
        SavedSearch s = new SavedSearch();
        s.setUser(user);
        s.setName(request.name().trim());
        s.setCriteria(request.criteria());
        s.setAlertEnabled(request.alertEnabled());
        s.setAlertMode(mode);
        s.setLastNotifiedAt(Instant.now());
        return mapper.toDto(repository.save(s));
    }

    @Transactional
    public SavedSearchDto update(Long userId, Long id, SavedSearchRequest request) {
        SavedSearch s = lockAndFind(userId, id);
        AlertMode mode = request.alertMode() == null ? s.getAlertMode() : request.alertMode();
        checkInstantLimit(s, request.alertEnabled(), mode);
        if (!Objects.equals(s.getCriteria(), request.criteria())) {
            s.setLastNotifiedAt(Instant.now());
        }
        s.setName(request.name().trim());
        s.setCriteria(request.criteria());
        s.setAlertEnabled(request.alertEnabled());
        s.setAlertMode(mode);
        return mapper.toDto(s);
    }

    @Transactional
    public SavedSearchDto patch(Long userId, Long id, SavedSearchPatchRequest request) {
        SavedSearch s = lockAndFind(userId, id);
        boolean enabled = request.alertEnabled() != null ? request.alertEnabled() : s.isAlertEnabled();
        AlertMode mode = request.alertMode() != null ? request.alertMode() : s.getAlertMode();
        checkInstantLimit(s, enabled, mode);
        if (request.name() != null && !request.name().isBlank()) {
            s.setName(request.name().trim());
        }
        if (enabled && !s.isAlertEnabled()) {
            s.setLastNotifiedAt(Instant.now());
        }
        s.setAlertEnabled(enabled);
        s.setAlertMode(mode);
        return mapper.toDto(s);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(find(userId, id));
    }

    private void checkInstantLimit(SavedSearch s, boolean enabled, AlertMode mode) {
        boolean wantsInstant = enabled && mode == AlertMode.INSTANT;
        long otherInstant = repository.countByUserIdAndAlertEnabledTrueAndAlertMode(s.getUser().getId(), AlertMode.INSTANT)
                - (s.isInstantAlert() ? 1 : 0);
        SavedSearchLimitPolicy.check(s.getUser().getRole(), limits, 0, otherInstant, false, wantsInstant);
    }

    private SavedSearch lockAndFind(Long userId, Long id) {
        userRepository.findByIdForUpdate(userId);
        return find(userId, id);
    }

    private SavedSearch find(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId).orElseThrow(() -> DomainException.notFound("Zapisane wyszukiwanie", id));
    }
}
