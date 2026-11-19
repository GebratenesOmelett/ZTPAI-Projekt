package pl.domradar.ingestion;

public enum ImportStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED;

    public boolean isActive() {
        return this == QUEUED || this == RUNNING;
    }
}
