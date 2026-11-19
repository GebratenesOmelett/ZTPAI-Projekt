package pl.domradar.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import pl.domradar.ingestion.dto.ImportJobDto;
import pl.domradar.source.SourceRepository;
import pl.domradar.support.ApiTestClient;
import pl.domradar.support.ImportTestSupport;
import pl.domradar.support.IntegrationTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static pl.domradar.support.ApiTestClient.bearer;

/** Pełny przepływ: API → RabbitMQ → worker → batche → normalizacja → deduplikacja → finalizacja. */
@IntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImportIT {

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

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    @Order(1)
    void adminStartsImportAndGets202WithLocation() throws Exception {
        String admin = ApiTestClient.adminToken(mvc, om);
        Long gratka = sources.findByCode("gratka").orElseThrow().getId();

        mvc.perform(post("/api/v1/admin/sources/{id}/imports", gratka).header("Authorization", bearer(admin)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/admin/import-jobs/")))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.sourceCode").value("gratka"));

        org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(120))
                .until(() -> !jobs.existsBySourceIdAndStatusIn(gratka, java.util.EnumSet.of(ImportStatus.QUEUED, ImportStatus.RUNNING)));
    }

    @Test
    @Order(2)
    void demoImportsFromTwoPortalsAreDeduplicated() {
        ImportJobDto otodom = ImportTestSupport.runImport(importService, jobs, sources, "otodom");
        ImportJobDto morizon = ImportTestSupport.runImport(importService, jobs, sources, "morizon");

        assertThat(otodom.status()).isEqualTo(ImportStatus.SUCCESS);
        assertThat(morizon.status()).isEqualTo(ImportStatus.SUCCESS);
        assertThat(otodom.fetched()).isPositive();
        assertThat(morizon.fetched()).isPositive();

        int properties = count("select count(*) from property");
        int offers = count("select count(*) from offer");
        assertThat(properties).isLessThan(offers);
        assertThat(count("select count(*) from property where active_source_count >= 2")).isPositive();
        // każda oferta ma wpis początkowy w historii cen
        assertThat(count("select count(*) from offer o where not exists (select 1 from price_history h where h.offer_id = o.id)")).isZero();
        // statystyki dzielnic zostały odświeżone po imporcie
        assertThat(count("select count(*) from district_price_stats")).isPositive();
    }

    @Test
    @Order(3)
    void snapshotImportParsesFilesAndCountsInvalidListingAsError() {
        ImportJobDto job = ImportTestSupport.runImport(importService, jobs, sources, "nieruchomosci-online");

        assertThat(job.status()).isEqualTo(ImportStatus.SUCCESS);
        assertThat(job.fetched()).isGreaterThanOrEqualTo(15);
        assertThat(job.errors()).isGreaterThanOrEqualTo(1);
        assertThat(count("select count(*) from offer o join source s on s.id = o.source_id where s.code = 'nieruchomosci-online'"))
                .isEqualTo(job.fetched() - job.errors());
    }

    @Test
    @Order(4)
    void repeatedImportsRecordPriceChangesOnlyOnRealChangeAndDeactivateMissingOffers() {
        int offersBefore = count("select count(*) from offer o join source s on s.id = o.source_id where s.code = 'otodom'");
        ImportJobDto run2 = ImportTestSupport.runImport(importService, jobs, sources, "otodom");
        ImportJobDto run3 = ImportTestSupport.runImport(importService, jobs, sources, "otodom");
        ImportJobDto run4 = ImportTestSupport.runImport(importService, jobs, sources, "otodom");

        assertThat(run2.status()).isEqualTo(ImportStatus.SUCCESS);
        // ponowny import nie duplikuje ofert (UNIQUE source_id + external_id)
        assertThat(count("select count(*) from (select source_id, external_id from offer group by 1, 2 having count(*) > 1) d")).isZero();
        assertThat(count("select count(*) from offer o join source s on s.id = o.source_id where s.code = 'otodom'"))
                .isGreaterThanOrEqualTo(offersBefore);
        // zmiany cen między importami zapisane w historii, bez wpisów dla niezmienionych cen
        assertThat(count("select count(*) from price_history")).isGreaterThan(count("select count(*) from offer"));
        assertThat(count("""
                select count(*) from (
                  select offer_id, price, lag(price) over (partition by offer_id order by changed_at, id) prev from price_history
                ) x where prev = price
                """)).isZero();
        // oferty znikające od 2. importu są dezaktywowane po 3 kolejnych udanych importach
        assertThat(run2.deactivated() + run3.deactivated() + run4.deactivated()).isPositive();
        assertThat(count("select count(*) from offer where not active and missed_imports >= 3")).isPositive();
        assertThat(count("""
                select count(*) from property p where p.status = 'ARCHIVED'
                  and exists (select 1 from offer o where o.property_id = p.id and o.active)
                """)).as("archived property must not have active offers").isZero();
    }

    @Test
    @Order(5)
    void importOfSourceWithRunningJobReturns409() throws Exception {
        String admin = ApiTestClient.adminToken(mvc, om);
        Long olx = sources.findByCode("olx").orElseThrow().getId();
        jdbc.update("insert into import_job (source_id, status, trigger_type, run_token) values (?, 'RUNNING', 'MANUAL', 'it-test')", olx);
        try {
            mvc.perform(post("/api/v1/admin/sources/{id}/imports", olx).header("Authorization", bearer(admin)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("IMPORT_ALREADY_RUNNING"));
        } finally {
            jdbc.update("delete from import_job where run_token = 'it-test'");
        }
    }

    @Test
    @Order(6)
    void importOfDisabledSourceOrDisabledLiveFetchingReturns422() throws Exception {
        String admin = ApiTestClient.adminToken(mvc, om);
        Long live = sources.findByCode("otodom-live").orElseThrow().getId();
        Long gratka = sources.findByCode("gratka").orElseThrow().getId();

        // portale live są w testach wyłączone flagą LIVE_SOURCES_ENABLED
        mvc.perform(post("/api/v1/admin/sources/{id}/imports", live).header("Authorization", bearer(admin)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("LIVE_FETCH_DISABLED"));

        jdbc.update("update source set enabled = false where id = ?", gratka);
        try {
            mvc.perform(post("/api/v1/admin/sources/{id}/imports", gratka).header("Authorization", bearer(admin)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("SOURCE_DISABLED"));
        } finally {
            jdbc.update("update source set enabled = true where id = ?", gratka);
        }
    }

    @Test
    @Order(7)
    void sourceCrudWithValidationAndConflicts() throws Exception {
        String admin = ApiTestClient.adminToken(mvc, om);
        Map<String, Object> body = Map.of("code", "domiporta", "name", "Domiporta", "baseUrl", "https://www.domiporta.pl",
                "adapter", "demo", "enabled", true, "importCron", "0 0 4 * * *");

        String location = mvc.perform(post("/api/v1/admin/sources").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern(".*/api/v1/admin/sources/\\d+$")))
                .andReturn().getResponse().getHeader("Location");

        mvc.perform(post("/api/v1/admin/sources").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SOURCE_CODE_TAKEN"));

        mvc.perform(post("/api/v1/admin/sources").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("code", "Zły Kod!", "name", "", "baseUrl", "nie-url",
                                "adapter", "demo", "enabled", true, "importCron", "co godzinę"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));

        mvc.perform(put(location).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("code", "domiporta", "name", "Domiporta", "baseUrl",
                                "https://www.domiporta.pl", "adapter", "nie-ma-takiego", "enabled", false))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errorCode").value("UNKNOWN_ADAPTER"));

        mvc.perform(delete(location).header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());

        Long otodom = sources.findByCode("otodom").orElseThrow().getId();
        mvc.perform(delete("/api/v1/admin/sources/{id}", otodom).header("Authorization", bearer(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SOURCE_IN_USE"));
    }

    @Test
    @Order(8)
    void regularUserGets403OnAdminEndpoints() throws Exception {
        String user = ApiTestClient.registerAndGetToken(mvc, om);

        mvc.perform(get("/api/v1/admin/sources").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/admin/import-jobs").header("Authorization", bearer(user)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/import-jobs").header("Authorization", bearer(ApiTestClient.adminToken(mvc, om))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.content[0].id").exists());
        mvc.perform(get("/api/v1/admin/sources")).andExpect(status().isUnauthorized());
    }

    @SuppressWarnings("unused")
    private static org.hamcrest.Matcher<String> endsWithPath(String suffix) {
        return endsWith(suffix);
    }
}
