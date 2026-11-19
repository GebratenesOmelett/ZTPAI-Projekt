package pl.domradar.ingestion.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.domradar.ingestion.event.ImportRequestedEvent;

/**
 * Wysyła ImportRequested do RabbitMQ dopiero po zatwierdzeniu transakcji zapisującej zadanie —
 * worker nigdy nie dostanie wiadomości o zadaniu, którego jeszcze nie ma w bazie.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportRequestPublisher {

    private final RabbitTemplate rabbitTemplate;

    @TransactionalEventListener
    public void onImportRequested(ImportRequestedEvent event) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.IMPORT_QUEUE, new ImportRequestedMessage(event.jobId()));
        log.debug("Published ImportRequested for job {}", event.jobId());
    }
}
