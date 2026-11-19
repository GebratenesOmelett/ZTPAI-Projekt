package pl.domradar.ingestion.event;

/** Zadanie importu zostało zapisane — po commicie trafia do kolejki RabbitMQ. */
public record ImportRequestedEvent(Long jobId) {
}
