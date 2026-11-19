package pl.domradar.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.common.exception.ErrorType;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Fabryka obiektów RFC 7807 ProblemDetail w jednolitym formacie API. */
public final class ProblemDetails {

    public static final String TYPE_BASE = "https://domradar.local/problems/";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatus status, ErrorCode code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create(TYPE_BASE + code.name().toLowerCase().replace('_', '-')));
        pd.setTitle(titleFor(status));
        pd.setProperty("errorCode", code.name());
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }

    public static ProblemDetail withFieldErrors(ProblemDetail pd, List<FieldErrorDto> errors) {
        pd.setProperty("fieldErrors", errors);
        return pd;
    }

    public static ProblemDetail withDetails(ProblemDetail pd, Map<String, Object> details) {
        if (details != null && !details.isEmpty()) {
            pd.setProperty("details", details);
        }
        return pd;
    }

    public static HttpStatus statusFor(ErrorType type) {
        return switch (type) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case BUSINESS_RULE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    private static String titleFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Niepoprawne żądanie";
            case UNAUTHORIZED -> "Wymagane uwierzytelnienie";
            case FORBIDDEN -> "Brak uprawnień";
            case NOT_FOUND -> "Nie znaleziono";
            case CONFLICT -> "Konflikt";
            case UNPROCESSABLE_ENTITY -> "Naruszenie reguły biznesowej";
            case METHOD_NOT_ALLOWED -> "Metoda niedozwolona";
            default -> status.getReasonPhrase();
        };
    }
}
