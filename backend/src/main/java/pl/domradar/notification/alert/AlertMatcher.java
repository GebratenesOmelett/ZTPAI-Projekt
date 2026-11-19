package pl.domradar.notification.alert;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import pl.domradar.ingestion.event.ImportCompletedEvent;
import pl.domradar.property.event.PriceDroppedEvent;
import pl.domradar.property.event.PropertyCreatedEvent;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zbiera eventy PropertyCreated / PriceDropped (po commicie) w obrębie importu i po jego zakończeniu
 * zleca jedno zbiorcze dopasowanie alertów ("3 nowe ogłoszenia dla 'Mokotów 3 pokoje'") zamiast
 * osobnego powiadomienia dla każdego ogłoszenia. Zdarzenia spoza importu (np. ręczne dodanie oferty
 * przez admina) są dopasowywane od razu. Bufor jest w pamięci — przy wielu instancjach należałoby go
 * przenieść do bazy lub kolejki (opisane w README).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlertMatcher {

    private record Pending(Set<Long> created, Set<Long> dropped) {
        Pending() {
            this(ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet());
        }
    }

    private final Map<Long, Pending> pendingByJob = new ConcurrentHashMap<>();
    private final AlertDispatcher dispatcher;

    @TransactionalEventListener(fallbackExecution = true)
    public void onPropertyCreated(PropertyCreatedEvent event) {
        if (event.importJobId() == null) {
            dispatcher.dispatchAsync(Set.of(event.propertyId()), Set.of());
        } else {
            pendingByJob.computeIfAbsent(event.importJobId(), id -> new Pending()).created().add(event.propertyId());
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onPriceDropped(PriceDroppedEvent event) {
        if (event.importJobId() == null) {
            dispatcher.dispatchAsync(Set.of(), Set.of(event.propertyId()));
        } else {
            pendingByJob.computeIfAbsent(event.importJobId(), id -> new Pending()).dropped().add(event.propertyId());
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onImportCompleted(ImportCompletedEvent event) {
        Pending pending = pendingByJob.remove(event.jobId());
        if (pending == null || (pending.created().isEmpty() && pending.dropped().isEmpty())) {
            return;
        }
        log.info("Matching alerts for import job {}: {} new properties, {} price drops",
                event.jobId(), pending.created().size(), pending.dropped().size());
        dispatcher.dispatchAsync(Set.copyOf(pending.created()), Set.copyOf(pending.dropped()));
    }
}
