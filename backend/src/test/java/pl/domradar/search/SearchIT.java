package pl.domradar.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import pl.domradar.ingestion.ImportJobRepository;
import pl.domradar.ingestion.ImportService;
import pl.domradar.source.SourceRepository;
import pl.domradar.support.ImportTestSupport;
import pl.domradar.support.IntegrationTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Wyszukiwarka na danych z DemoAdapter: filtry, sortowanie wielokluczowe, paginacja, fasety, walidacja. */
@IntegrationTest
class SearchIT {

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

    Long warsawId;

    @BeforeEach
    void data() {
        ImportTestSupport.ensureDemoData(importService, jobs, sources, jdbc);
        warsawId = jdbc.queryForObject("select id from city where slug = 'warszawa'", Long.class);
    }

    private JsonNode search(Object criteria, String query) throws Exception {
        String body = mvc.perform(post("/api/v1/properties/search" + query)
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(criteria)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return om.readTree(body);
    }

    @Test
    void filtersAreAppliedToAllResults() throws Exception {
        Map<String, Object> criteria = Map.of("transactionType", "SALE", "propertyTypes", List.of("APARTMENT"),
                "cityIds", List.of(warsawId), "rooms", List.of(2, 3), "priceMax", 2_000_000, "notGroundFloor", true);

        JsonNode result = search(criteria, "?size=100");

        assertThat(result.get("totalElements").asLong()).isPositive();
        for (JsonNode p : result.get("content")) {
            assertThat(p.get("city").asText()).isEqualTo("Warszawa");
            assertThat(p.get("transactionType").asText()).isEqualTo("SALE");
            assertThat(p.get("rooms").asInt()).isBetween(2, 3);
            assertThat(p.get("floor").asInt()).isPositive();
            assertThat(new BigDecimal(p.get("price").asText())).isLessThanOrEqualTo(new BigDecimal("2000000"));
        }
    }

    @Test
    void multiKeySortingAndPaginationAreConsistent() throws Exception {
        Map<String, Object> criteria = Map.of("transactionType", "SALE");

        JsonNode page0 = search(criteria, "?page=0&size=10&sort=pricePerM2,asc&sort=area,desc");
        JsonNode page1 = search(criteria, "?page=1&size=10&sort=pricePerM2,asc&sort=area,desc");

        assertThat(page0.get("sort").toString()).contains("pricePerM2,asc", "area,desc");
        assertThat(page0.get("content")).hasSize(10);
        assertThat(page0.get("totalPages").asInt()).isEqualTo((int) Math.ceil(page0.get("totalElements").asDouble() / 10));
        List<BigDecimal> prices = new ArrayList<>();
        page0.get("content").forEach(p -> prices.add(new BigDecimal(p.get("pricePerM2").asText())));
        page1.get("content").forEach(p -> prices.add(new BigDecimal(p.get("pricePerM2").asText())));
        assertThat(prices).isSortedAccordingTo(BigDecimal::compareTo);
        List<Long> ids0 = new ArrayList<>();
        page0.get("content").forEach(p -> ids0.add(p.get("id").asLong()));
        page1.get("content").forEach(p -> assertThat(ids0).doesNotContain(p.get("id").asLong()));
    }

    @Test
    void facetsAndMedianComparisonAreReturned() throws Exception {
        JsonNode result = search(Map.of("cityIds", List.of(warsawId), "transactionType", "SALE"), "?size=5");

        JsonNode facets = result.get("facets");
        assertThat(facets.get("districts")).isNotEmpty();
        assertThat(facets.get("rooms")).isNotEmpty();
        assertThat(facets.get("propertyTypes")).isNotEmpty();
        assertThat(facets.get("sources")).isNotEmpty();
        assertThat(facets.get("priceHistogram")).isNotEmpty();
        long histogramTotal = 0;
        for (JsonNode bucket : facets.get("priceHistogram")) {
            histogramTotal += bucket.get("count").asLong();
        }
        assertThat(histogramTotal).isEqualTo(result.get("totalElements").asLong());
        assertThat(result.get("content").get(0).has("vsMedianPercent")).isTrue();
    }

    @Test
    void fullTextSearchWithExclusionAndTypoTolerance() throws Exception {
        JsonNode withBalcony = search(Map.of("q", "balkon"), "?size=100&facets=false");
        JsonNode typo = search(Map.of("q", "mkotow"), "?size=100&facets=false");
        JsonNode excluded = search(Map.of("q", "-parter"), "?size=100&facets=false");
        JsonNode all = search(Map.of(), "?size=1&facets=false");

        assertThat(withBalcony.get("totalElements").asLong()).isPositive();
        assertThat(typo.get("totalElements").asLong()).as("typo-tolerant match for Mokotów").isPositive();
        assertThat(excluded.get("totalElements").asLong()).isLessThan(all.get("totalElements").asLong());
        Integer groundFloorInExcluded = jdbc.queryForObject("""
                select count(*) from property p where p.status = 'ACTIVE'
                  and p.search_vector @@ to_tsquery('domradar_pl', 'parte:*')""", Integer.class);
        assertThat(all.get("totalElements").asLong() - excluded.get("totalElements").asLong()).isEqualTo(groundFloorInExcluded.longValue());
    }

    @Test
    void geoRadiusAndDistanceSorting() throws Exception {
        JsonNode result = search(Map.of("lat", 52.2297, "lon", 21.0122, "radiusKm", 5), "?size=50&sort=distance,asc&facets=false");

        List<Double> distances = new ArrayList<>();
        result.get("content").forEach(p -> distances.add(p.get("distanceMeters").asDouble()));
        assertThat(distances).isNotEmpty().allSatisfy(d -> assertThat(d).isLessThanOrEqualTo(5000.0)).isSorted();
    }

    @Test
    void getWithQueryParamsGivesSameResultAsPost() throws Exception {
        JsonNode viaPost = search(Map.of("cityIds", List.of(warsawId), "rooms", List.of(3)), "?size=5&facets=false");

        mvc.perform(get("/api/v1/properties/search").param("cityIds", String.valueOf(warsawId)).param("rooms", "3")
                        .param("size", "5").param("facets", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(viaPost.get("totalElements").asLong()));
        mvc.perform(get("/api/v1/properties/search").param("bbox", "52.0,20.8,52.4,21.3").param("size", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidCriteriaReturn400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/properties/search").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("priceMin", 900000, "priceMax", 100000))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("priceMin"));
        mvc.perform(post("/api/v1/properties/search").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("lat", 52.2, "lon", 21.0, "radiusKm", 80))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("radiusKm"));
        mvc.perform(get("/api/v1/properties/search").param("priceMin", "10").param("priceMax", "5"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/properties/search?sort=hackme").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/properties/search?size=500").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void propertyDetailsIncludeOffersHistoryAndMedian() throws Exception {
        Long id = jdbc.queryForObject("select id from property where active_source_count >= 2 and district_id is not null limit 1", Long.class);

        mvc.perform(get("/api/v1/properties/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offers.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.offers[0].priceHistory").isArray())
                .andExpect(jsonPath("$.offers[0].sourceName").isNotEmpty())
                .andExpect(jsonPath("$.median.districtMedianPricePerM2").isNumber())
                .andExpect(jsonPath("$.version").isNumber());
        mvc.perform(get("/api/v1/properties/{id}", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/properties/compare").param("ids", id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/properties/compare").param("ids", "1,2,3,4,5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dictionariesStatsAndMapPoints() throws Exception {
        mvc.perform(get("/api/v1/dictionaries/cities")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(17));
        mvc.perform(get("/api/v1/dictionaries/districts").param("cityId", warsawId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].cityId").value(warsawId));
        mvc.perform(get("/api/v1/dictionaries/features")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/stats/districts").param("cityId", warsawId.toString()).param("transactionType", "SALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].medianPricePerM2").isNumber());
        mvc.perform(post("/api/v1/properties/search/map").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("cityIds", List.of(warsawId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].lat").isNumber());
    }
}
