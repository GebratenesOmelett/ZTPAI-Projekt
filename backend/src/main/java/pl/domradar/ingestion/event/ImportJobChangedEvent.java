package pl.domradar.ingestion.event;

import pl.domradar.ingestion.dto.ImportJobDto;

/** Zmiana stanu/postępu zadania importu — przekazywana do panelu admina przez WebSocket. */
public record ImportJobChangedEvent(ImportJobDto job) {
}
