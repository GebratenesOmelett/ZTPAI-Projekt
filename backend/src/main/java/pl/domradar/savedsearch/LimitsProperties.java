package pl.domradar.savedsearch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Limity zapisanych wyszukiwań dla roli USER (reguła biznesowa nr 5). */
@ConfigurationProperties("app.limits")
public record LimitsProperties(int maxSavedSearches, int maxInstantAlerts) {

    public LimitsProperties {
        if (maxSavedSearches <= 0) {
            maxSavedSearches = 10;
        }
        if (maxInstantAlerts <= 0) {
            maxInstantAlerts = 3;
        }
    }
}
