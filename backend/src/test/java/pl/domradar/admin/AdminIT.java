package pl.domradar.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pl.domradar.ingestion.ImportJobRepository;
import pl.domradar.ingestion.ImportService;
import pl.domradar.source.SourceRepository;
import pl.domradar.support.ApiTestClient;
import pl.domradar.support.ImportTestSupport;
import pl.domradar.support.IntegrationTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static pl.domradar.support.ApiTestClient.bearer;

/** Panel admina: użytkownicy (reguła 6), edycja z optimistic locking (reguła 7), scalanie/rozdzielanie, ręczne oferty. */
@IntegrationTest
class AdminIT {

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

    String admin;

    @BeforeEach
    void setUp() throws Exception {
        ImportTestSupport.ensureDemoData(importService, jobs, sources, jdbc);
        admin = ApiTestClient.adminToken(mvc, om);
    }

    private String json(Object o) throws Exception {
        return om.writeValueAsString(o);
    }

    private JsonNode read(ResultActions actions) throws Exception {
        return om.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private Long adminId() {
        return jdbc.queryForObject("select id from app_user where email = 'admin@test.local'", Long.class);
    }

    @Test
    void adminCannotDemoteHimselfNorTheLastAdmin() throws Exception {
        mvc.perform(patch("/api/v1/admin/users/{id}", adminId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("role", "USER"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SELF_MODIFICATION"));
        mvc.perform(delete("/api/v1/admin/users/{id}", adminId()).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict());

        // drugi admin może zdegradować pierwszego tylko, gdy ten nie jest ostatnim aktywnym adminem
        String secondToken = ApiTestClient.registerAndGetToken(mvc, om);
        Long secondId = jdbc.queryForObject("select id from app_user order by id desc limit 1", Long.class);
        mvc.perform(patch("/api/v1/admin/users/{id}", secondId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(patch("/api/v1/admin/users/{id}", secondId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("role", "USER"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("USER"));
        jdbc.update("update app_user set enabled = false where role = 'ADMIN' and id <> ?", adminId());
        mvc.perform(patch("/api/v1/admin/users/{id}", secondId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk());
        // teraz admin@test.local i second są adminami — ale jeśli second zostanie zablokowany, admin jest ostatnim
        mvc.perform(patch("/api/v1/admin/users/{id}", secondId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", false))))
                .andExpect(status().isOk());
        String secondAsAdmin = secondToken;
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(secondAsAdmin)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blockedUserCannotLogIn() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", "blocked@test.local", "password", "Haslo12345", "displayName", "Zablokowany"))));
        Long id = jdbc.queryForObject("select id from app_user where email = 'blocked@test.local'", Long.class);

        mvc.perform(patch("/api/v1/admin/users/{id}", id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("enabled", false))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "blocked@test.local", "password", "Haslo12345"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCOUNT_DISABLED"));
        mvc.perform(get("/api/v1/admin/users").param("q", "blocked").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void propertyEditUsesOptimisticLocking() throws Exception {
        Long id = jdbc.queryForObject("select id from property where district_id is not null order by id limit 1", Long.class);
        JsonNode details = read(mvc.perform(get("/api/v1/admin/properties/{id}", id).header("Authorization", bearer(admin))));
        long version = details.get("version").asLong();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("propertyType", details.get("propertyType").asText());
        attributes.put("transactionType", details.get("transactionType").asText());
        attributes.put("cityId", details.get("cityId").asLong());
        attributes.put("districtId", details.get("districtId").asLong());
        attributes.put("street", "Poprawiona 1");
        attributes.put("lat", details.get("lat").asDouble());
        attributes.put("lon", details.get("lon").asDouble());
        attributes.put("area", 77.7);
        attributes.put("rooms", 4);
        attributes.put("features", List.of("BALCONY"));

        JsonNode updated = read(mvc.perform(put("/api/v1/admin/properties/{id}", id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version", version, "attributes", attributes))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.area").value(77.7)));
        long newVersion = updated.get("version").asLong();
        assertThat(newVersion).isGreaterThan(version);

        // drugi zapis z tą samą (już nieaktualną) wersją → konflikt
        mvc.perform(put("/api/v1/admin/properties/{id}", id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version", version, "attributes", attributes))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.details.currentVersion").value(newVersion));

        // wersja zwrócona w odpowiedzi jest aktualna — kolejna edycja z nią się udaje
        attributes.put("rooms", 3);
        mvc.perform(put("/api/v1/admin/properties/{id}", id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version", newVersion, "attributes", attributes))))
                .andExpect(status().isOk());
        newVersion = read(mvc.perform(get("/api/v1/admin/properties/{id}", id).header("Authorization", bearer(admin)))).get("version").asLong();

        Long otherCityDistrict = jdbc.queryForObject(
                "select d.id from district d where d.city_id <> ? limit 1", Long.class, details.get("cityId").asLong());
        attributes.put("districtId", otherCityDistrict);
        mvc.perform(put("/api/v1/admin/properties/{id}", id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("version", newVersion, "attributes", attributes))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFERENCE"));
    }

    @Test
    void splitAndMergeRoundTrip() throws Exception {
        Long id = jdbc.queryForObject("""
                select p.id from property p where (select count(*) from offer o where o.property_id = p.id) >= 2
                order by p.id limit 1""", Long.class);
        JsonNode details = read(mvc.perform(get("/api/v1/admin/properties/{id}", id).header("Authorization", bearer(admin))));
        int offerCount = details.get("offers").size();
        long offerToMove = details.get("offers").get(offerCount - 1).get("id").asLong();

        JsonNode split = read(mvc.perform(post("/api/v1/admin/properties/{id}/split", id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("offerIds", List.of(offerToMove), "version", details.get("version").asLong()))))
                .andExpect(status().isCreated()));
        long newId = split.get("created").get("id").asLong();
        assertThat(split.get("original").get("offers")).hasSize(offerCount - 1);
        assertThat(split.get("created").get("offers")).hasSize(1);

        mvc.perform(post("/api/v1/admin/properties/{id}/split", id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("offerIds", List.of(offerToMove), "version", split.get("original").get("version").asLong()))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("SPLIT_NOT_ALLOWED"));

        JsonNode merged = read(mvc.perform(post("/api/v1/admin/properties/{id}/merge", id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourcePropertyId", newId,
                                "targetVersion", split.get("original").get("version").asLong(),
                                "sourceVersion", split.get("created").get("version").asLong()))))
                .andExpect(status().isOk()));
        assertThat(merged.get("offers")).hasSize(offerCount);
        mvc.perform(get("/api/v1/properties/{id}", newId)).andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/admin/properties/{id}/merge", id).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("sourcePropertyId", id, "targetVersion", 0, "sourceVersion", 0))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("MERGE_NOT_ALLOWED"));
    }

    @Test
    void manualOfferIsAddedThroughDeduplicationAndCanBeEditedAndDeleted() throws Exception {
        Long cityId = jdbc.queryForObject("select id from city where slug = 'krakow'", Long.class);
        Long districtId = jdbc.queryForObject("select id from district where slug = 'krowodrza' and city_id = ?", Long.class, cityId);
        Map<String, Object> attributes = Map.of("propertyType", "APARTMENT", "transactionType", "SALE", "cityId", cityId,
                "districtId", districtId, "street", "Testowa 5", "area", 51.0, "rooms", 2, "floor", 3, "totalFloors", 5);

        JsonNode offer = read(mvc.perform(post("/api/v1/admin/offers").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("externalId", "MAN-IT-1", "title", "Ręcznie dodane mieszkanie", "price", 777000,
                                "attributes", attributes))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceCode").value("manual"))
                .andExpect(jsonPath("$.priceHistory.length()").value(1)));
        long offerId = offer.get("id").asLong();

        mvc.perform(post("/api/v1/admin/offers").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("externalId", "MAN-IT-1", "title", "Duplikat", "price", 1, "attributes", attributes))))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/v1/admin/offers/{id}", offerId).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "Ręcznie dodane mieszkanie", "price", 740000, "active", true, "privateSeller", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(740000))
                .andExpect(jsonPath("$.priceHistory.length()").value(2));

        mvc.perform(get("/api/v1/admin/offers").param("propertyId", offer.get("propertyId").asText()).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").exists());
        mvc.perform(delete("/api/v1/admin/offers/{id}", offerId).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/admin/offers/{id}", offerId).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }
}
