package pl.domradar.property;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "photo")
@Getter
@Setter
@NoArgsConstructor
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id")
    private Offer offer;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private int position;

    public Photo(Offer offer, String url, int position) {
        this.offer = offer;
        this.url = url;
        this.position = position;
    }
}
