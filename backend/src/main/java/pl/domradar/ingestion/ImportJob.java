package pl.domradar.ingestion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.domradar.source.Source;

import java.time.Instant;

@Entity
@Table(name = "import_job")
@Getter
@Setter
@NoArgsConstructor
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id")
    private Source source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportStatus status = ImportStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false)
    private ImportTrigger trigger = ImportTrigger.MANUAL;

    @Column(name = "requested_by")
    private String requestedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    private int fetched;
    private int created;
    private int updated;
    private int merged;
    private int deactivated;
    private int errors;

    @Column(name = "expected_batches")
    private Integer expectedBatches;

    @Column(name = "processed_batches", nullable = false)
    private int processedBatches;

    @Column(name = "error_message")
    private String errorMessage;

    public ImportJob(Source source, ImportTrigger trigger, String requestedBy) {
        this.source = source;
        this.trigger = trigger;
        this.requestedBy = requestedBy;
    }
}
