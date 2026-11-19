package pl.domradar.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Liczba nieprzeczytanych powiadomień")
public record UnreadCountDto(@Schema(example = "3") long unread) {
}
