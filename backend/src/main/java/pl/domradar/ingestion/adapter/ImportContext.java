package pl.domradar.ingestion.adapter;

import java.time.Instant;

/**
 * Kontekst pojedynczego uruchomienia importu przekazywany do adaptera.
 *
 * @param runNumber numer kolejnego udanego importu danego źródła (1 = pierwszy) — DemoAdapter
 *                  używa go jako "czasu", by symulować zmiany cen i znikające ogłoszenia
 */
public record ImportContext(Long jobId, String sourceCode, String sourceName, String baseUrl,
                            int runNumber, Instant startedAt) {
}
