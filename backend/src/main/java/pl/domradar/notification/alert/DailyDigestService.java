package pl.domradar.notification.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pl.domradar.common.util.PolishPlural;
import pl.domradar.notification.NotificationService;
import pl.domradar.notification.NotificationType;
import pl.domradar.notification.mail.MailService;
import pl.domradar.savedsearch.AlertMode;
import pl.domradar.savedsearch.SavedSearch;
import pl.domradar.savedsearch.SavedSearchRepository;
import pl.domradar.search.SearchService;
import pl.domradar.search.dto.PropertySummaryDto;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dzienny digest dla alertów w trybie DAILY: codziennie o 7:00 (Europe/Warsaw) dla każdego wyszukiwania
 * zbiera nieruchomości, które pojawiły się od ostatniego powiadomienia, i wysyła e-mail + powiadomienie.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyDigestService {

    private static final int LISTINGS_IN_DIGEST = 10;

    private final SavedSearchRepository savedSearchRepository;
    private final SearchService searchService;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final Clock clock;

    @Scheduled(cron = "${app.alerts.daily-digest-cron:0 0 7 * * *}", zone = "Europe/Warsaw")
    public void scheduledDigest() {
        int sent = runDigest();
        log.info("Daily digest finished: {} digests sent", sent);
    }

    /** @return liczba wysłanych digestów */
    public int runDigest() {
        Instant now = clock.instant();
        int sent = 0;
        for (SavedSearch search : savedSearchRepository.findByAlertEnabledTrueAndAlertMode(AlertMode.DAILY)) {
            if (!search.getUser().isEnabled()) {
                continue;
            }
            Instant since = search.getLastNotifiedAt() != null ? search.getLastNotifiedAt() : now.minus(Duration.ofDays(1));
            SearchService.NewMatches matches = searchService.newMatchesSince(search.getCriteria(), since, LISTINGS_IN_DIGEST);
            if (matches.total() > 0) {
                String title = "Dzienny przegląd: " + PolishPlural.count(matches.total(), "nowe ogłoszenie", "nowe ogłoszenia", "nowych ogłoszeń")
                        + " dla '" + search.getName() + "'";
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("savedSearchId", search.getId());
                payload.put("savedSearchName", search.getName());
                payload.put("newCount", matches.total());
                payload.put("newPropertyIds", matches.top().stream().map(PropertySummaryDto::id).toList());
                notificationService.create(search.getUser().getId(), NotificationType.DAILY_DIGEST, title, payload);
                mailService.sendAlert(search.getUser().getEmail(), search.getUser().getDisplayName(), "DomRadar: " + title,
                        title + ". Najnowsze z nich:", matches.top().stream()
                                .map(p -> new MailService.ListingLine(p.id(), p.title(), p.price(),
                                        p.city() + (p.district() != null ? ", " + p.district() : "")))
                                .toList(), search.getId());
                sent++;
            }
            savedSearchRepository.updateLastNotifiedAt(search.getId(), now);
        }
        return sent;
    }
}
