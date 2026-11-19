package pl.domradar.favorite;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    @EntityGraph(attributePaths = {"property", "property.city", "property.district"})
    Page<Favorite> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"property", "property.city", "property.district"})
    Optional<Favorite> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndPropertyId(Long userId, Long propertyId);
}
