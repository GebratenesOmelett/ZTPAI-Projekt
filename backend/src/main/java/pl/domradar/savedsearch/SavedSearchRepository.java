package pl.domradar.savedsearch;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {

    List<SavedSearch> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<SavedSearch> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndAlertEnabledTrueAndAlertMode(Long userId, AlertMode alertMode);

    @EntityGraph(attributePaths = "user")
    List<SavedSearch> findByAlertEnabledTrueAndAlertMode(AlertMode alertMode);
}
