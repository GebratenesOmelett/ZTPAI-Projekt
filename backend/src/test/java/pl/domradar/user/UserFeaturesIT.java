package pl.domradar.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.MockMvc;
import pl.domradar.ingestion.ImportJobRepository;
import pl.domradar.ingestion.ImportService;
import pl.domradar.notification.alert.AlertDispatcher;
import pl.domradar.source.SourceRepository;
import pl.domradar.support.ApiTestClient;
import pl.domradar.support.ImportTestSupport;
import pl.domradar.support.IntegrationTest;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static pl.domradar.support.ApiTestClient.bearer;

/** Funkcje użytkownika: zapisane wyszukiwania (limity), ulubione (CRUD), alerty i powiadomienia. */
@IntegrationTest
class UserFeaturesIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper om;
    @Autowired
    ImportService importService;
    @Autowired
    ImportJobRepository jobs;
    @Autowired
    SourceRepository sources;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    AlertDispatcher alertDispatcher;

    @Autowired
    JavaMailSender mailSender;

    String token;

    @BeforeEach
    void setUp() throws Exception {
        ImportTestSupport.ensureDemoData(importService, jobs, sources, jdbc);
        token = ApiTestClient.registerAndGetToken(mvc, om);
        when(mailSender.createMimeMessage()).thenReturn(new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null));
    }

    private String json(Object o) throws Exception {
        return om.writeValueAsString(o);
    }

    private Map<String, Object> savedSearch(String name, boolean alert, String mode) {
        return Map.of("name", name, "criteria", Map.of("transactionType", "SALE", "rooms", List.of(3)),
                "alertEnabled", alert, "alertMode", mode);
    }

    @Test
    void savedSearchCrudAndLimitsReturn422() throws Exception {
        String location = mvc.perform(post("/api/v1/me/saved-searches").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(savedSearch("Pierwsze", true, "INSTANT"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/me/saved-searches/")))
                .andExpect(jsonPath("$.criteria.rooms[0]").value(3))
                .andReturn().getResponse().getHeader("Location");

        for (int i = 2; i <= 3; i++) {
            mvc.perform(post("/api/v1/me/saved-searches").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(json(savedSearch("Instant " + i, true, "INSTANT"))))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/me/saved-searches").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(savedSearch("Czwarty instant", true, "INSTANT"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INSTANT_ALERT_LIMIT"));

        for (int i = 4; i <= 10; i++) {
            mvc.perform(post("/api/v1/me/saved-searches").header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON).content(json(savedSearch("Daily " + i, true, "DAILY"))))
                    .andExpect(status().isCreated());
        }
        mvc.perform(post("/api/v1/me/saved-searches").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(savedSearch("Jedenaste", false, "DAILY"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SAVED_SEARCH_LIMIT"));

        mvc.perform(get("/api/v1/me/saved-searches").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(10));
        mvc.perform(patch(location).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Zmieniona", "alertEnabled", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Zmieniona"))
                .andExpect(jsonPath("$.alertEnabled").value(false));
        mvc.perform(put(location).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Zła", "criteria", Map.of("priceMin", 10, "priceMax", 5), "alertEnabled", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("criteria.priceMin"));
        mvc.perform(delete(location).header("Authorization", bearer(token))).andExpect(status().isNoContent());
        mvc.perform(get(location).header("Authorization", bearer(token))).andExpect(status().isNotFound());
    }

    @Test
    void favoritesCrudWithNotesAndConflict() throws Exception {
        Long propertyId = jdbc.queryForObject("select id from property where status = 'ACTIVE' order by id limit 1", Long.class);

        String location = mvc.perform(post("/api/v1/me/favorites").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("propertyId", propertyId, "note", "Do obejrzenia"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.note").value("Do obejrzenia"))
                .andExpect(jsonPath("$.archived").value(false))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(post("/api/v1/me/favorites").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("propertyId", propertyId))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("FAVORITE_EXISTS"));
        mvc.perform(post("/api/v1/me/favorites").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("propertyId", 987654321))))
                .andExpect(status().isNotFound());

        mvc.perform(patch(location).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("note", "Cena do negocjacji"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.note").value("Cena do negocjacji"));
        mvc.perform(get("/api/v1/me/favorites").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].propertyId").value(propertyId));
        mvc.perform(get("/api/v1/properties/{id}", propertyId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.favoriteId").isNumber());

        // inny użytkownik nie widzi i nie może zmienić cudzych ulubionych
        String other = ApiTestClient.registerAndGetToken(mvc, om);
        mvc.perform(patch(location).header("Authorization", bearer(other)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("note", "hack")))).andExpect(status().isNotFound());

        mvc.perform(delete(location).header("Authorization", bearer(token))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/favorites").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/me/favorites")).andExpect(status().isUnauthorized());
    }

    @Test
    void instantAlertCreatesNotificationAndSendsEmail() throws Exception {
        Long propertyId = jdbc.queryForObject(
                "select id from property where status = 'ACTIVE' and transaction_type = 'SALE' and rooms = 3 order by id limit 1", Long.class);
        mvc.perform(post("/api/v1/me/saved-searches").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(savedSearch("Alert 3 pokoje", true, "INSTANT"))))
                .andExpect(status().isCreated());

        int matched = alertDispatcher.dispatch(Set.of(propertyId), Set.of());

        assertThat(matched).isPositive();
        String body = mvc.perform(get("/api/v1/me/notifications").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("NEW_LISTINGS"))
                .andExpect(jsonPath("$.content[0].title").value("1 nowe ogłoszenie dla 'Alert 3 pokoje'"))
                .andReturn().getResponse().getContentAsString();
        JsonNode notification = om.readTree(body).get("content").get(0);
        assertThat(notification.get("payload").get("newPropertyIds").get(0).asLong()).isEqualTo(propertyId);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> verify(mailSender, atLeastOnce()).send(any(MimeMessage.class)));

        mvc.perform(get("/api/v1/me/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.unread").value(1));
        mvc.perform(patch("/api/v1/me/notifications/{id}", notification.get("id").asLong()).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("read", true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
        mvc.perform(get("/api/v1/me/notifications/unread-count").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.unread").value(0));
    }

    @Test
    void dailyDigestCanBeTriggeredByAdmin() throws Exception {
        mvc.perform(post("/api/v1/me/saved-searches").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(json(savedSearch("Codzienny", true, "DAILY"))))
                .andExpect(status().isCreated());
        // cofamy znacznik ostatniego powiadomienia, aby digest objął wszystkie istniejące nieruchomości
        jdbc.update("update saved_search set last_notified_at = now() - interval '30 days' where name = 'Codzienny'");

        mvc.perform(post("/api/v1/admin/alerts/daily-digest").header("Authorization", bearer(ApiTestClient.adminToken(mvc, om))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sent").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/v1/me/notifications").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.content[0].type").value("DAILY_DIGEST"));
        mvc.perform(post("/api/v1/admin/alerts/daily-digest").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }
}
