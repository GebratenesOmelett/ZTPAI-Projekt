package pl.domradar.favorite;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.domradar.property.Property;
import pl.domradar.user.User;

import java.time.Instant;

@Entity
@Table(name = "favorite", uniqueConstraints = @UniqueConstraint(name = "ux_favorite_user_property", columnNames = {"user_id", "property_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id")
    private Property property;

    @Column(length = 2000)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Favorite(User user, Property property, String note) {
        this.user = user;
        this.property = property;
        this.note = note;
    }
}
