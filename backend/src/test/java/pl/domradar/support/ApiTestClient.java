package pl.domradar.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Pomocnik testów integracyjnych: rejestracja/logowanie i pobieranie access tokenu. */
public final class ApiTestClient {

    private ApiTestClient() {
    }

    public static String registerAndGetToken(MockMvc mvc, ObjectMapper om) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@test.local";
        MvcResult result = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", "Haslo12345", "displayName", "Tester"))))
                .andReturn();
        return om.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    public static String login(MockMvc mvc, ObjectMapper om, String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", password))))
                .andReturn();
        JsonNode body = om.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    public static String adminToken(MockMvc mvc, ObjectMapper om) throws Exception {
        return login(mvc, om, "admin@test.local", "AdminPass123!");
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
