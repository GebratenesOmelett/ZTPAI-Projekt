package pl.domradar.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Oznaczenie powiadomienia jako przeczytane / nieprzeczytane")
public record NotificationPatchRequest(@Schema(example = "true") @NotNull Boolean read) {
}
