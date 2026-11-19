package pl.domradar.notification.event;

import pl.domradar.notification.dto.NotificationDto;

/** Nowe powiadomienie zapisane w bazie — po commicie wysyłane przez WebSocket do właściciela. */
public record NotificationCreatedEvent(Long userId, NotificationDto notification) {
}
