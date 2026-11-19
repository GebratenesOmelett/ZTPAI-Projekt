package pl.domradar.savedsearch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.savedsearch.dto.SavedSearchPatchRequest;
import pl.domradar.savedsearch.dto.SavedSearchRequest;
import pl.domradar.search.SearchCriteria;
import pl.domradar.user.Role;
import pl.domradar.user.User;
import pl.domradar.user.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Reguła biznesowa nr 5 — limity zapisanych wyszukiwań i alertów INSTANT. */
@ExtendWith(MockitoExtension.class)
class SavedSearchServiceTest {

    @Mock
    SavedSearchRepository repository;
    @Mock
    UserRepository userRepository;

    SavedSearchService service;
    User user;

    @BeforeEach
    void setUp() {
        service = new SavedSearchService(repository, userRepository, Mappers.getMapper(SavedSearchMapper.class),
                new LimitsProperties(10, 3));
        user = new User("u@test.pl", "x", "U", Role.USER);
        user.setId(5L);
    }

    private static SavedSearchRequest request(boolean alert, AlertMode mode) {
        return new SavedSearchRequest("Mokotów", SearchCriteria.empty(), alert, mode);
    }

    private static ErrorCode codeOf(Throwable t) {
        return ((DomainException) t).getCode();
    }

    @Test
    void eleventhSavedSearchIsRejected() {
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(user));
        when(repository.countByUserId(5L)).thenReturn(10L);

        assertThatThrownBy(() -> service.create(5L, request(false, AlertMode.DAILY)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.SAVED_SEARCH_LIMIT));
        verify(repository, never()).save(any());
    }

    @Test
    void fourthInstantAlertIsRejectedButDailyAllowed() {
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(user));
        when(repository.countByUserId(5L)).thenReturn(4L);
        when(repository.countByUserIdAndAlertEnabledTrueAndAlertMode(5L, AlertMode.INSTANT)).thenReturn(3L);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.create(5L, request(true, AlertMode.INSTANT)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INSTANT_ALERT_LIMIT));
        assertThat(service.create(5L, request(true, AlertMode.DAILY)).alertMode()).isEqualTo(AlertMode.DAILY);
    }

    @Test
    void adminIsNotLimited() {
        user.setRole(Role.ADMIN);
        when(userRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(user));
        when(repository.countByUserId(5L)).thenReturn(50L);
        when(repository.countByUserIdAndAlertEnabledTrueAndAlertMode(5L, AlertMode.INSTANT)).thenReturn(20L);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(5L, request(true, AlertMode.INSTANT)).alertEnabled()).isTrue();
    }

    @Test
    void enablingInstantAlertViaPatchRespectsLimitExcludingItself() {
        SavedSearch existing = new SavedSearch();
        existing.setId(9L);
        existing.setUser(user);
        existing.setName("A");
        existing.setCriteria(SearchCriteria.empty());
        existing.setAlertEnabled(false);
        existing.setAlertMode(AlertMode.DAILY);
        when(repository.findByIdAndUserId(9L, 5L)).thenReturn(Optional.of(existing));
        when(repository.countByUserIdAndAlertEnabledTrueAndAlertMode(5L, AlertMode.INSTANT)).thenReturn(3L);

        assertThatThrownBy(() -> service.patch(5L, 9L, new SavedSearchPatchRequest(null, true, AlertMode.INSTANT)))
                .satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INSTANT_ALERT_LIMIT));

        // wyszukiwanie, które już jest INSTANT, można edytować przy pełnym limicie
        existing.setAlertEnabled(true);
        existing.setAlertMode(AlertMode.INSTANT);
        assertThat(service.patch(5L, 9L, new SavedSearchPatchRequest("Nowa nazwa", null, null)).name()).isEqualTo("Nowa nazwa");
    }
}
