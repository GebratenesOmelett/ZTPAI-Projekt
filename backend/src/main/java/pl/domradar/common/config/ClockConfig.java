package pl.domradar.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Zegar jako bean — logika zależna od czasu (okno 30 dni obniżek, cykl życia) jest testowalna. */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
