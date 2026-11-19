package pl.domradar.property.dictionary;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "district")
@Getter
@Setter
@NoArgsConstructor
public class District {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "city_id")
    private City city;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    /** Aliasy rozdzielone pionową kreską, wykorzystywane przy mapowaniu nazw z portali. */
    @Column(nullable = false)
    private String aliases = "";

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    public List<String> aliasList() {
        if (aliases == null || aliases.isBlank()) {
            return List.of();
        }
        return Arrays.stream(aliases.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
