package pl.domradar.common.web;

import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;

import java.util.ArrayList;
import java.util.List;

/**
 * Globalna obsługa błędów. Każda odpowiedź błędu ma format RFC 7807 (application/problem+json)
 * z dodatkowymi polami {@code errorCode}, {@code timestamp} i — dla walidacji — {@code fieldErrors}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomain(DomainException ex) {
        HttpStatus status = ProblemDetails.statusFor(ex.getCode().type());
        ProblemDetail pd = ProblemDetails.withDetails(ProblemDetails.of(status, ex.getCode(), ex.getMessage()), ex.getDetails());
        return ResponseEntity.status(status).body(pd);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ProblemDetail> handleOptimisticLock(Exception ex) {
        log.info("Optimistic lock conflict: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, ErrorCode.VERSION_CONFLICT,
                "Zasób został w międzyczasie zmieniony przez kogoś innego. Odśwież dane i spróbuj ponownie.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.info("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, ErrorCode.DATA_CONFLICT, "Operacja narusza spójność danych (np. duplikat).");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDto> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorDto(lastNode(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        ProblemDetail pd = ProblemDetails.of(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Dane wejściowe są niepoprawne");
        return ResponseEntity.badRequest().body(ProblemDetails.withFieldErrors(pd, errors));
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ProblemDetail> handleAccessDenied(Exception ex) {
        return problem(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Brak uprawnień do tego zasobu");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Wymagane uwierzytelnienie");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Wystąpił nieoczekiwany błąd serwera");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        List<FieldErrorDto> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(new FieldErrorDto(fe.getField(), fe.getDefaultMessage()));
        }
        for (ObjectError oe : ex.getBindingResult().getGlobalErrors()) {
            errors.add(new FieldErrorDto(oe.getObjectName(), oe.getDefaultMessage()));
        }
        ProblemDetail pd = ProblemDetails.of(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Dane wejściowe są niepoprawne");
        return ResponseEntity.badRequest().body(ProblemDetails.withFieldErrors(pd, errors));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex, HttpHeaders headers,
                                                                            HttpStatusCode status, WebRequest request) {
        List<FieldErrorDto> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String param = result.getMethodParameter().getParameterName();
            for (MessageSourceResolvable error : result.getResolvableErrors()) {
                String field = error instanceof FieldError fe ? fe.getField() : param;
                errors.add(new FieldErrorDto(field, error.getDefaultMessage()));
            }
        });
        ProblemDetail pd = ProblemDetails.of(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Dane wejściowe są niepoprawne");
        return ResponseEntity.badRequest().body(ProblemDetails.withFieldErrors(pd, errors));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(ProblemDetails.of(HttpStatus.BAD_REQUEST, ErrorCode.MALFORMED_REQUEST,
                "Nie można odczytać treści żądania (niepoprawny JSON lub typ danych)"));
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String name = ex.getPropertyName() == null ? "parametr" : ex.getPropertyName();
        ProblemDetail pd = ProblemDetails.of(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Niepoprawna wartość parametru: " + name);
        return ResponseEntity.badRequest().body(ProblemDetails.withFieldErrors(pd,
                List.of(new FieldErrorDto(name, "niepoprawna wartość: " + ex.getValue()))));
    }

    /** Uzupełnia standardowe odpowiedzi Springa (404, 405, 415...) o pole errorCode. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail pd
                && (pd.getProperties() == null || !pd.getProperties().containsKey("errorCode"))) {
            ErrorCode code = statusCode.value() == 404 ? ErrorCode.NOT_FOUND
                    : statusCode.is4xxClientError() ? ErrorCode.MALFORMED_REQUEST : ErrorCode.INTERNAL_ERROR;
            pd.setProperty("errorCode", code.name());
        }
        return response;
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, ErrorCode code, String detail) {
        return ResponseEntity.status(status).body(ProblemDetails.of(status, code, detail));
    }

    private static String lastNode(String path) {
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
