package pl.domradar.savedsearch;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SavedSearchRepository extends JpaRepository<SavedSearch, Long> {

    List<SavedSearch> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<SavedSearch> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndAlertEnabledTrueAndAlertMode(Long userId, AlertMode alertMode);

    @EntityGraph(attributePaths = "user")
    List<SavedSearch> findByAlertEnabledTrueAndAlertMode(AlertMode alertMode);

    @Transactional
    @Modifying
    @Query("update SavedSearch s set s.lastNotifiedAt = :at where s.id = :id")
    int updateLastNotifiedAt(@Param("id") Long id, @Param("at") Instant at);
}
