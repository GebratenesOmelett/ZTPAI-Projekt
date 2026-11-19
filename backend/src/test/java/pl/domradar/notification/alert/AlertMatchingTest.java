package pl.domradar.notification.alert;

import org.junit.jupiter.api.Test;
import pl.domradar.property.TransactionType;
import pl.domradar.savedsearch.AlertMode;
import pl.domradar.savedsearch.SavedSearch;
import pl.domradar.search.SearchCriteria;
import pl.domradar.user.Role;
import pl.domradar.user.User;

import java.util.*;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;

class AlertMatchingTest {

    /** Sztuczny "silnik": wyszukiwanie SALE pasuje do parzystych id, RENT do nieparzystych. */
    private static final BiFunction<SearchCriteria, Collection<Long>, List<Long>> MATCHER = (criteria, ids) ->
            ids.stream().filter(id -> criteria.transactionType() == TransactionType.SALE ? id % 2 == 0 : id % 2 == 1).toList();

    private static SavedSearch search(long id, TransactionType tx, boolean userEnabled) {
        User user = new User("u" + id + "@test.pl", "x", "U", Role.USER);
        user.setId(id);
        user.setEnabled(userEnabled);
        SavedSearch s = new SavedSearch();
        s.setId(id);
        s.setUser(user);
        s.setName("S" + id);
        s.setAlertEnabled(true);
        s.setAlertMode(AlertMode.INSTANT);
        s.setCriteria(SearchCriteria.builder().transactionType(tx).build());
        return s;
    }

    @Test
    void eachSearchGetsOnlyItsMatchingNewListingsAndPriceDrops() {
        List<AlertMatching.Match> matches = AlertMatching.match(
                List.of(search(1, TransactionType.SALE, true), search(2, TransactionType.RENT, true)),
                Set.of(10L, 11L, 12L), Set.of(20L, 21L), MATCHER);

        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).newPropertyIds()).containsExactly(10L, 12L);
        assertThat(matches.get(0).priceDropPropertyIds()).containsExactly(20L);
        assertThat(matches.get(1).newPropertyIds()).containsExactly(11L);
        assertThat(matches.get(1).priceDropPropertyIds()).containsExactly(21L);
    }

    @Test
    void searchesWithoutMatchesOrOfBlockedUsersAreSkipped() {
        List<AlertMatching.Match> matches = AlertMatching.match(
                List.of(search(1, TransactionType.SALE, true), search(3, TransactionType.SALE, false)),
                Set.of(11L, 13L), Set.of(), MATCHER);

        assertThat(matches).isEmpty();
    }

    @Test
    void newPropertyIsNotReportedTwiceAsPriceDrop() {
        List<AlertMatching.Match> matches = AlertMatching.match(List.of(search(1, TransactionType.SALE, true)),
                Set.of(10L), Set.of(10L, 30L), MATCHER);

        assertThat(matches.getFirst().newPropertyIds()).containsExactly(10L);
        assertThat(matches.getFirst().priceDropPropertyIds()).containsExactly(30L);
        assertThat(matches.getFirst().total()).isEqualTo(2);
    }

    @Test
    void noEventsMeansNoMatching() {
        assertThat(AlertMatching.match(List.of(search(1, TransactionType.SALE, true)), Set.of(), Set.of(), MATCHER)).isEmpty();
    }

    @Test
    void notificationTitleUsesPolishPlurals() {
        assertThat(AlertNotifier.buildTitle(3, 0, "Mokotów 3 pokoje")).isEqualTo("3 nowe ogłoszenia dla 'Mokotów 3 pokoje'");
        assertThat(AlertNotifier.buildTitle(1, 0, "X")).isEqualTo("1 nowe ogłoszenie dla 'X'");
        assertThat(AlertNotifier.buildTitle(12, 5, "X")).isEqualTo("12 nowych ogłoszeń i 5 obniżek cen dla 'X'");
        assertThat(AlertNotifier.buildTitle(0, 2, "X")).isEqualTo("2 obniżki cen dla 'X'");
    }
}
