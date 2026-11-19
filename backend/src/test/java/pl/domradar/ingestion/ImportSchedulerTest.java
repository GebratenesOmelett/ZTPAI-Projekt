package pl.domradar.ingestion;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** Harmonogram importów: raz dziennie o 4:00 (Europe/Warsaw), z nadrabianiem pominiętego importu. */
class ImportSchedulerTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final String DAILY_4AM = "0 0 4 * * *";

    @Test
    void importIsNotRepeatedTheSameDay() {
        Instant lastRun = Instant.parse("2026-11-19T22:35:00Z");      // 23:35 czasu polskiego
        assertThat(ImportScheduler.isDue(DAILY_4AM, lastRun, Instant.parse("2026-11-20T02:59:00Z"), WARSAW)).isFalse();
        assertThat(ImportScheduler.isDue(DAILY_4AM, lastRun, Instant.parse("2026-11-20T03:00:00Z"), WARSAW)).isTrue();
    }

    @Test
    void importAfterTodaysRunWaitsUntilNextDay() {
        Instant lastRun = Instant.parse("2026-11-20T03:00:10Z");      // dzisiejszy import o 4:00
        assertThat(ImportScheduler.isDue(DAILY_4AM, lastRun, Instant.parse("2026-11-20T20:00:00Z"), WARSAW)).isFalse();
        assertThat(ImportScheduler.isDue(DAILY_4AM, lastRun, Instant.parse("2026-11-21T03:00:30Z"), WARSAW)).isTrue();
    }

    @Test
    void missedImportIsCaughtUpAfterRestart() {
        Instant lastRun = Instant.parse("2026-11-17T03:00:00Z");      // aplikacja była wyłączona przez 2 dni
        assertThat(ImportScheduler.isDue(DAILY_4AM, lastRun, Instant.parse("2026-11-19T12:00:00Z"), WARSAW)).isTrue();
    }
}
