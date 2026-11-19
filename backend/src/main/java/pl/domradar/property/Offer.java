package pl.domradar.property;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pl.domradar.source.Source;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Ogłoszenie z konkretnego portalu, przypięte do zdeduplikowanej nieruchomości. */
@Entity
@Table(name = "offer", uniqueConstraints = @UniqueConstraint(name = "ux_offer_source_external", columnNames = {"source_id", "external_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id")
    private Property property;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id")
    private Source source;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    private String url;

    @Column(nullable = false)
    private String title;

    private String description;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal price;

    @Column(name = "agency_name")
    private String agencyName;

    @Column(name = "private_seller", nullable = false)
    private boolean privateSeller;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "missed_imports", nullable = false)
    private int missedImports;

    @Column(name = "last_seen_job_id")
    private Long lastSeenJobId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private OfferAttributes attributes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "offer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt asc")
    private List<PriceHistory> priceHistory = new ArrayList<>();

    @OneToMany(mappedBy = "offer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position asc")
    private List<Photo> photos = new ArrayList<>();

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

    public void addPriceHistory(BigDecimal price, Instant at) {
        PriceHistory entry = new PriceHistory(this, price, at);
        priceHistory.add(entry);
    }

    public void replacePhotos(List<String> urls) {
        photos.clear();
        for (int i = 0; i < urls.size(); i++) {
            photos.add(new Photo(this, urls.get(i), i));
        }
    }
}
