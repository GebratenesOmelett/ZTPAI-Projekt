package pl.domradar.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Włącza @Async (wysyłka e-maili, dopasowanie alertów) oraz @Scheduled (importy cron, digest dzienny). */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
