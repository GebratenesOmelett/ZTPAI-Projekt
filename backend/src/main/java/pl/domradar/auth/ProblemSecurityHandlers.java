package pl.domradar.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.common.web.ProblemDetails;

import java.io.IOException;
import java.net.URI;

/** Odpowiedzi 401/403 z filtrów Spring Security w tym samym formacie ProblemDetail co reszta API. */
@Component
public class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        write(request, response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED,
                "Wymagane uwierzytelnienie (brak lub nieważny token dostępu)");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Brak uprawnień do tego zasobu");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                       ErrorCode code, String detail) throws IOException {
        ProblemDetail pd = ProblemDetails.of(status, code, detail);
        pd.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), pd);
    }
}
