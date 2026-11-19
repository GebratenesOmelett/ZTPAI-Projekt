package pl.domradar.common.exception;

/**
 * Kategoria błędu domenowego. Warstwa serwisów nie zna HTTP — mapowanie kategorii
 * na kody statusu odbywa się wyłącznie w {@code GlobalExceptionHandler}.
 */
public enum ErrorType {
    VALIDATION,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    BUSINESS_RULE,
    INTERNAL
}
