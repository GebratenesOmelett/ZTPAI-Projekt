package pl.domradar.ingestion.dedup;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.domradar.ingestion.normalize.NormalizedListing;

import java.util.Comparator;
import java.util.Optional;

/** Szuka istniejącej nieruchomości, do której należy dołączyć nowe ogłoszenie (reguła biznesowa nr 1). */
@Slf4j
@Service
public class DeduplicationService {

    public record Match(Long propertyId, int score) {
    }

    private final PropertyCandidateRepository candidates;
    private final DedupProperties properties;

    public DeduplicationService(PropertyCandidateRepository candidates, DedupProperties properties) {
        this.candidates = candidates;
        this.properties = properties;
    }

    public Optional<Match> findMatch(NormalizedListing listing, Long sourceId) {
        return candidates.findCandidates(listing, sourceId, properties.candidateRadiusMeters()).stream()
                .map(c -> new Match(c.id(), DedupScorer.score(listing, c).total()))
                .filter(m -> m.score() >= properties.threshold())
                .max(Comparator.comparingInt(Match::score))
                .map(m -> {
                    log.debug("Listing {} matched property {} with score {}", listing.externalId(), m.propertyId(), m.score());
                    return m;
                });
    }
}
