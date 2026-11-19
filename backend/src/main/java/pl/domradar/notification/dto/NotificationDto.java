package pl.domradar.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.domradar.notification.NotificationType;

import java.time.Instant;
import java.util.Map;

@Schema(description = "Powiadomienie użytkownika")
public record NotificationDto(
        Long id,
        NotificationType type,
        @Schema(example = "3 nowe ogłoszenia dla 'Mokotów 3 pokoje'") String title,
        @Schema(description = "Dane: savedSearchId, savedSearchName, propertyIds, count") Map<String, Object> payload,
        boolean read,
        Instant createdAt) {
}
