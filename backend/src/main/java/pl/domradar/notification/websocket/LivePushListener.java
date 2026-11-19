package pl.domradar.notification.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.domradar.ingestion.event.ImportJobChangedEvent;
import pl.domradar.notification.event.NotificationCreatedEvent;

/** Wysyłka zdarzeń na żywo przez STOMP — po zatwierdzeniu transakcji, żeby klient nie zobaczył niezapisanych danych. */
@Slf4j
@Component
@RequiredArgsConstructor
public class LivePushListener {

    private final SimpMessagingTemplate messaging;

    @TransactionalEventListener(fallbackExecution = true)
    public void onNotification(NotificationCreatedEvent event) {
        messaging.convertAndSendToUser(String.valueOf(event.userId()), WebSocketConfig.USER_NOTIFICATIONS, event.notification());
        log.debug("Pushed notification {} to user {}", event.notification().id(), event.userId());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onImportJobChanged(ImportJobChangedEvent event) {
        messaging.convertAndSend(WebSocketConfig.ADMIN_IMPORT_JOBS, event.job());
    }
}
