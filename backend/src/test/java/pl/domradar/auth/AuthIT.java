package pl.domradar.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pl.domradar.support.IntegrationTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static pl.domradar.support.ApiTestClient.bearer;

@IntegrationTest
class AuthIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;

    private String json(Object o) throws Exception {
        return om.writeValueAsString(o);
    }

    @Test
    void registerLoginMeRefreshAndLogoutFlow() throws Exception {
        Map<String, String> body = Map.of("email", "Anna@Example.com", "password", "Haslo12345", "displayName", "Anna");

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/auth/me")))
                .andExpect(header().string("Set-Cookie", allOf(containsString("domradar_refresh="), containsString("HttpOnly"))))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("anna@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"));

        MvcResult login = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "anna@example.com", "password", "Haslo12345"))))
                .andExpect(status().isOk())
                .andReturn();
        String token = om.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();
        Cookie refreshCookie = login.getResponse().getCookie("domradar_refresh");
        assertThat(refreshCookie).isNotNull();

        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Anna"));

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie rotated = refreshed.getResponse().getCookie("domradar_refresh");
        assertThat(rotated.getValue()).isNotEqualTo(refreshCookie.getValue());

        // ponowne użycie starego (zrotowanego) tokenu jest odrzucane
        mvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));

        mvc.perform(post("/api/v1/auth/logout").cookie(rotated))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
    }

    @Test
    void registerWithTakenEmailReturns409() throws Exception {
        Map<String, String> body = Map.of("email", "dup@example.com", "password", "Haslo12345", "displayName", "Dup");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_TAKEN"));
    }

    @Test
    void registerWithInvalidDataReturnsFieldErrors() throws Exception {
        Map<String, String> body = Map.of("email", "not-an-email", "password", "short", "displayName", "");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItems("email", "password", "displayName")));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "admin@test.local", "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointWithoutTokenReturns401ProblemDetail() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer garbage.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void seededAdminCanLogIn() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "admin@test.local", "password", "AdminPass123!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }
}
