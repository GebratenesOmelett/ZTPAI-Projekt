package pl.domradar.ingestion.dedup;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param threshold             minimalny wynik scoringu (0–100), od którego ogłoszenie trafia do istniejącej nieruchomości
 * @param candidateRadiusMeters promień wyszukiwania kandydatów w bazie (PostGIS ST_DWithin)
 */
@ConfigurationProperties("app.dedup")
public record DedupProperties(int threshold, int candidateRadiusMeters) {

    public DedupProperties {
        if (threshold <= 0) {
            threshold = 70;
        }
        if (candidateRadiusMeters <= 0) {
            candidateRadiusMeters = 600;
        }
    }
}
