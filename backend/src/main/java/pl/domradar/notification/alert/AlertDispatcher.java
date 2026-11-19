package pl.domradar.notification.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import pl.domradar.savedsearch.AlertMode;
import pl.domradar.savedsearch.SavedSearchRepository;
import pl.domradar.search.SearchService;

import java.util.List;
import java.util.Set;

/** Asynchroniczne dopasowanie alertów INSTANT do nowych nieruchomości i obniżek cen. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertDispatcher {

    private final SavedSearchRepository savedSearchRepository;
    private final SearchService searchService;
    private final AlertNotifier notifier;

    @Async
    public void dispatchAsync(Set<Long> createdIds, Set<Long> droppedIds) {
        dispatch(createdIds, droppedIds);
    }

    public int dispatch(Set<Long> createdIds, Set<Long> droppedIds) {
        List<AlertMatching.Match> matches = AlertMatching.match(
                savedSearchRepository.findByAlertEnabledTrueAndAlertMode(AlertMode.INSTANT),
                createdIds, droppedIds, searchService::matchingIds);
        for (AlertMatching.Match match : matches) {
            try {
                notifier.notifyInstant(match);
            } catch (RuntimeException e) {
                log.warn("Cannot notify saved search {}: {}", match.savedSearch().getId(), e.getMessage());
            }
        }
        return matches.size();
    }
}
