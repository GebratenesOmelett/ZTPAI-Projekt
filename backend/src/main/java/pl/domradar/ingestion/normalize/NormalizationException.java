package pl.domradar.ingestion.normalize;

/** Ogłoszenia nie da się znormalizować (brak lub niepoprawne pole obowiązkowe). Liczone jako błąd importu. */
public class NormalizationException extends RuntimeException {

    public NormalizationException(String message) {
        super(message);
    }
}
