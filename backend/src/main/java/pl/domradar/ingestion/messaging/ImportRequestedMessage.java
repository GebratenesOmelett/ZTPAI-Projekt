package pl.domradar.ingestion.messaging;

/** Wiadomość ImportRequested (kolejka domradar.import.requests). */
public record ImportRequestedMessage(Long jobId) {
}
