package pl.domradar.ingestion.event;

import pl.domradar.ingestion.ImportStatus;

/** Import zakończony (SUCCESS lub FAILED) — po commicie: odświeżenie statystyk, unieważnienie cache, alerty. */
public record ImportCompletedEvent(Long jobId, Long sourceId, ImportStatus status) {
}
