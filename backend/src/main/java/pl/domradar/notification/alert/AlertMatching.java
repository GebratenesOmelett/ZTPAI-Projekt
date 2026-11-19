package pl.domradar.notification.alert;

import pl.domradar.savedsearch.SavedSearch;
import pl.domradar.search.SearchCriteria;

import java.util.*;
import java.util.function.BiFunction;

/**
 * Czysta logika dopasowania alertów: dla każdego zapisanego wyszukiwania z aktywnym alertem
 * wybiera nowe nieruchomości i obniżki cen spełniające jego kryteria. Właściwe sprawdzenie kryteriów
 * jest wstrzykiwane (w produkcji: to samo zapytanie SQL co wyszukiwarka), co pozwala testować regułę jednostkowo.
 */
public final class AlertMatching {

    public record Match(SavedSearch savedSearch, List<Long> newPropertyIds, List<Long> priceDropPropertyIds) {

        public int total() {
            return newPropertyIds.size() + priceDropPropertyIds.size();
        }
    }

    private AlertMatching() {
    }

    public static List<Match> match(Collection<SavedSearch> searches, Set<Long> createdIds, Set<Long> droppedIds,
                                    BiFunction<SearchCriteria, Collection<Long>, List<Long>> matcher) {
        if (createdIds.isEmpty() && droppedIds.isEmpty()) {
            return List.of();
        }
        Set<Long> dropsOnly = new TreeSet<>(droppedIds);
        dropsOnly.removeAll(createdIds);
        List<Match> matches = new ArrayList<>();
        for (SavedSearch search : searches) {
            if (!search.isAlertEnabled() || search.getUser() == null || !search.getUser().isEnabled()) {
                continue;
            }
            List<Long> newIds = createdIds.isEmpty() ? List.of() : sorted(matcher.apply(search.getCriteria(), createdIds));
            List<Long> dropIds = dropsOnly.isEmpty() ? List.of() : sorted(matcher.apply(search.getCriteria(), dropsOnly));
            if (!newIds.isEmpty() || !dropIds.isEmpty()) {
                matches.add(new Match(search, newIds, dropIds));
            }
        }
        return matches;
    }

    private static List<Long> sorted(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().distinct().sorted().toList();
    }
}
