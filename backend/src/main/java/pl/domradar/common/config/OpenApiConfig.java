package pl.domradar.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "DomRadar API",
        version = "v1",
        description = """
                Agregator ogłoszeń nieruchomości z wielu portali.

                **Uwierzytelnianie:** zaloguj się przez `POST /api/v1/auth/login`, skopiuj `accessToken`
                i wklej go w okno *Authorize* (schemat `bearerAuth`). Access token jest ważny 15 minut;
                nowy uzyskasz przez `POST /api/v1/auth/refresh` (refresh token w ciasteczku httpOnly).

                **Błędy:** wszystkie odpowiedzi błędów mają format RFC 7807 (`application/problem+json`)
                z polami `errorCode`, `timestamp` oraz — przy walidacji — `fieldErrors`.
                """))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT",
        description = "Access token JWT z POST /api/v1/auth/login")
public class OpenApiConfig {
}
