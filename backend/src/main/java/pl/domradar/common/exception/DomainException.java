package pl.domradar.common.exception;

import java.util.Map;

/**
 * Wyjątek domenowy rzucany przez warstwę serwisów. Niesie kod błędu i opcjonalne szczegóły,
 * nie zawiera żadnych informacji o HTTP.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> details;

    public DomainException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public DomainException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public static DomainException notFound(String resource, Object id) {
        return new DomainException(ErrorCode.NOT_FOUND, resource + " o identyfikatorze " + id + " nie istnieje",
                Map.of("resource", resource, "id", String.valueOf(id)));
    }
}
