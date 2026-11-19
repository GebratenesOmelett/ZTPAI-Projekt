package pl.domradar.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties("app.ingestion")
public record IngestionProperties(
        boolean bootstrapImport,
        boolean demoDataEnabled,
        int batchSize,
        int missedImportsToDeactivate,
        boolean schedulerEnabled,
        String snapshotsDir,
        BigDecimal eurRate,
        Demo demo
) {

    public IngestionProperties {
        if (batchSize <= 0) {
            batchSize = 50;
        }
        if (missedImportsToDeactivate <= 0) {
            missedImportsToDeactivate = 3;
        }
        if (eurRate == null) {
            eurRate = new BigDecimal("4.30");
        }
        if (demo == null) {
            demo = new Demo(20261119L, 1600);
        }
    }

    public record Demo(long seed, int universeSize) {
    }
}
