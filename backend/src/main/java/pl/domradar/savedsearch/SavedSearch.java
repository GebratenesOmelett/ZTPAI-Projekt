package pl.domradar.savedsearch;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pl.domradar.search.SearchCriteria;
import pl.domradar.user.User;

import java.time.Instant;

@Entity
@Table(name = "saved_search")
@Getter
@Setter
@NoArgsConstructor
public class SavedSearch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private SearchCriteria criteria;

    @Column(name = "alert_enabled", nullable = false)
    private boolean alertEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_mode", nullable = false)
    private AlertMode alertMode = AlertMode.DAILY;

    @Column(name = "last_notified_at")
    private Instant lastNotifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isInstantAlert() {
        return alertEnabled && alertMode == AlertMode.INSTANT;
    }
}
