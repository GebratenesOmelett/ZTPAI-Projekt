package pl.domradar.notification.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.domradar.common.util.PolishPlural;
import pl.domradar.notification.NotificationService;
import pl.domradar.notification.NotificationType;
import pl.domradar.notification.mail.MailService;
import pl.domradar.property.Property;
import pl.domradar.property.PropertyRepository;
import pl.domradar.savedsearch.SavedSearch;
import pl.domradar.savedsearch.SavedSearchRepository;
import pl.domradar.user.User;

import java.time.Instant;
import java.util.*;

/** Tworzy powiadomienie w bazie (+ push WebSocket po commicie) i wysyła e-mail dla dopasowanego alertu. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertNotifier {

    private static final int MAX_IDS_IN_PAYLOAD = 20;
    private static final int MAX_LISTINGS_IN_MAIL = 10;

    private final NotificationService notificationService;
    private final MailService mailService;
    private final PropertyRepository propertyRepository;
    private final SavedSearchRepository savedSearchRepository;

    public List<MailService.ListingLine> listingLines(Collection<Long> ids) {
        List<Long> limited = ids.stream().limit(MAX_LISTINGS_IN_MAIL).toList();
        Map<Long, Property> byId = new HashMap<>();
        propertyRepository.findByIdIn(limited).forEach(p -> byId.put(p.getId(), p));
        List<MailService.ListingLine> lines = new ArrayList<>();
        for (Long id : limited) {
            Property p = byId.get(id);
            if (p != null) {
                String location = p.getCity().getName() + (p.getDistrict() != null ? ", " + p.getDistrict().getName() : "");
                lines.add(new MailService.ListingLine(p.getId(), p.getTitle(), p.getCurrentPrice(), location));
            }
        }
        return lines;
    }

    public void notifyInstant(AlertMatching.Match match) {
        SavedSearch search = match.savedSearch();
        User user = search.getUser();
        int newCount = match.newPropertyIds().size();
        int dropCount = match.priceDropPropertyIds().size();
        String title = buildTitle(newCount, dropCount, search.getName());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("savedSearchId", search.getId());
        payload.put("savedSearchName", search.getName());
        payload.put("newCount", newCount);
        payload.put("priceDropCount", dropCount);
        payload.put("newPropertyIds", match.newPropertyIds().stream().limit(MAX_IDS_IN_PAYLOAD).toList());
        payload.put("priceDropPropertyIds", match.priceDropPropertyIds().stream().limit(MAX_IDS_IN_PAYLOAD).toList());
        NotificationType type = newCount > 0 ? NotificationType.NEW_LISTINGS : NotificationType.PRICE_DROP;
        notificationService.create(user.getId(), type, title, payload);

        List<Long> forMail = new ArrayList<>(match.newPropertyIds());
        forMail.addAll(match.priceDropPropertyIds());
        mailService.sendAlert(user.getEmail(), user.getDisplayName(), "DomRadar: " + title, title + ":",
                listingLines(forMail), search.getId());
        savedSearchRepository.updateLastNotifiedAt(search.getId(), Instant.now());
        log.info("Instant alert '{}' for user {}: {} new, {} price drops", search.getName(), user.getId(), newCount, dropCount);
    }

    static String buildTitle(int newCount, int dropCount, String searchName) {
        List<String> parts = new ArrayList<>();
        if (newCount > 0) {
            parts.add(PolishPlural.count(newCount, "nowe ogłoszenie", "nowe ogłoszenia", "nowych ogłoszeń"));
        }
        if (dropCount > 0) {
            parts.add(PolishPlural.count(dropCount, "obniżka ceny", "obniżki cen", "obniżek cen"));
        }
        return String.join(" i ", parts) + " dla '" + searchName + "'";
    }
}
