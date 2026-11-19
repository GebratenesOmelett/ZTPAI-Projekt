package pl.domradar.common.exception;

/** Kody błędów zwracane klientowi w polu {@code errorCode} ProblemDetail. */
public enum ErrorCode {
    VALIDATION_FAILED(ErrorType.VALIDATION),
    MALFORMED_REQUEST(ErrorType.VALIDATION),

    UNAUTHORIZED(ErrorType.UNAUTHORIZED),
    INVALID_CREDENTIALS(ErrorType.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN(ErrorType.UNAUTHORIZED),
    ACCOUNT_DISABLED(ErrorType.FORBIDDEN),
    ACCESS_DENIED(ErrorType.FORBIDDEN),

    NOT_FOUND(ErrorType.NOT_FOUND),

    EMAIL_TAKEN(ErrorType.CONFLICT),
    SOURCE_CODE_TAKEN(ErrorType.CONFLICT),
    SOURCE_IN_USE(ErrorType.CONFLICT),
    FAVORITE_EXISTS(ErrorType.CONFLICT),
    IMPORT_ALREADY_RUNNING(ErrorType.CONFLICT),
    LAST_ADMIN(ErrorType.CONFLICT),
    SELF_MODIFICATION(ErrorType.CONFLICT),
    VERSION_CONFLICT(ErrorType.CONFLICT),
    DATA_CONFLICT(ErrorType.CONFLICT),

    SOURCE_DISABLED(ErrorType.BUSINESS_RULE),
    UNKNOWN_ADAPTER(ErrorType.BUSINESS_RULE),
    SAVED_SEARCH_LIMIT(ErrorType.BUSINESS_RULE),
    INSTANT_ALERT_LIMIT(ErrorType.BUSINESS_RULE),
    MERGE_NOT_ALLOWED(ErrorType.BUSINESS_RULE),
    SPLIT_NOT_ALLOWED(ErrorType.BUSINESS_RULE),
    INVALID_REFERENCE(ErrorType.BUSINESS_RULE),

    INTERNAL_ERROR(ErrorType.INTERNAL);

    private final ErrorType type;

    ErrorCode(ErrorType type) {
        this.type = type;
    }

    public ErrorType type() {
        return type;
    }
}
