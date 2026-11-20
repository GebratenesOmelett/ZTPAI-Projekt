package pl.domradar.property;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;
import pl.domradar.property.dictionary.City;
import pl.domradar.property.dictionary.District;
import pl.domradar.property.dictionary.Feature;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Zdeduplikowana nieruchomość. Jedna nieruchomość może mieć wiele ofert z różnych portali.
 * Pole {@link #version} zapewnia optimistic locking przy edycji przez administratora.
 */
@Entity
@Table(name = "property")
@Getter
@Setter
@NoArgsConstructor
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false)
    private PropertyType propertyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "market_type")
    private MarketType marketType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id")
    private City city;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private District district;

    private String street;

    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal area;

    private Integer rooms;
    private Integer floor;

    @Column(name = "total_floors")
    private Integer totalFloors;

    @Column(name = "year_built")
    private Integer yearBuilt;

    @Column(nullable = false)
    private String title;

    @Column(name = "main_photo_url")
    private String mainPhotoUrl;

    @Column(name = "current_price", precision = 14, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "price_per_m2", precision = 12, scale = 2)
    private BigDecimal pricePerM2;

    @Column(name = "price_dropped", nullable = false)
    private boolean priceDropped;

    @Column(name = "price_drop_percent", nullable = false, precision = 6, scale = 2)
    private BigDecimal priceDropPercent = BigDecimal.ZERO;

    @Column(name = "last_price_change_at")
    private Instant lastPriceChangeAt;

    @Column(name = "last_price_drop_at")
    private Instant lastPriceDropAt;

    @Column(name = "active_source_count", nullable = false)
    private int activeSourceCount;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyStatus status = PropertyStatus.ACTIVE;

    @Column(name = "search_text", nullable = false)
    private String searchText = "";

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ManyToMany
    @JoinTable(name = "property_feature",
            joinColumns = @JoinColumn(name = "property_id"),
            inverseJoinColumns = @JoinColumn(name = "feature_id"))
    private Set<Feature> features = new HashSet<>();

    @OneToMany(mappedBy = "property")
    @OrderBy("price asc")
    private List<Offer> offers = new ArrayList<>();

    public boolean isArchived() {
        return status == PropertyStatus.ARCHIVED;
    }
}
