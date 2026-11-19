package pl.domradar;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.domradar.property.dictionary.CityRepository;
import pl.domradar.property.dictionary.DistrictRepository;
import pl.domradar.property.dictionary.FeatureRepository;
import pl.domradar.source.SourceRepository;
import pl.domradar.support.IntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Migracje Flyway przechodzą, encje zgadzają się ze schematem (ddl-auto=validate), słowniki są wczytane. */
@IntegrationTest
class SchemaIT {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CityRepository cities;
    @Autowired
    DistrictRepository districts;
    @Autowired
    FeatureRepository features;
    @Autowired
    SourceRepository sources;

    @Test
    void migrationsCreateExtensionsAndDictionaries() {
        assertThat(jdbc.queryForList("select extname from pg_extension", String.class))
                .contains("postgis", "pg_trgm", "unaccent");
        assertThat(cities.count()).isEqualTo(6);
        assertThat(districts.count()).isGreaterThan(40);
        assertThat(features.findByCode("BALCONY")).isPresent();
        assertThat(sources.findByCode("otodom-live")).hasValueSatisfying(s -> assertThat(s.getAdapter()).isEqualTo("otodom-http"));
        assertThat(sources.findByCode("olx-live")).hasValueSatisfying(s -> assertThat(s.getAdapter()).isEqualTo("olx-api"));
    }

    @Test
    void polishTextSearchConfigurationIgnoresDiacritics() {
        Boolean matches = jdbc.queryForObject(
                "select to_tsvector('domradar_pl', 'Słoneczne mieszkanie na Żoliborzu') @@ to_tsquery('domradar_pl', 'zoliborz:*')",
                Boolean.class);
        assertThat(matches).isTrue();
    }
}
