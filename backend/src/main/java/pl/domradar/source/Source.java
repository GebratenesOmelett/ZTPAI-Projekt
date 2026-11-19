package pl.domradar.source;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Portal (źródło ogłoszeń). Pole {@code adapter} wskazuje implementację SourceAdapter. */
@Entity
@Table(name = "source")
@Getter
@Setter
@NoArgsConstructor
public class Source {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(nullable = false)
    private String adapter;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "import_cron")
    private String importCron;

    @Column(name = "last_import_at")
    private Instant lastImportAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
