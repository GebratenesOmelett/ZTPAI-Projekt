package pl.domradar.ingestion.messaging;

import pl.domradar.ingestion.adapter.RawListing;

import java.util.List;

/** Paczka surowych ogłoszeń do normalizacji i deduplikacji (kolejka domradar.listing.batches). */
public record ListingBatchMessage(Long jobId, Long sourceId, String runToken, int batchNo, List<RawListing> listings) {
}
