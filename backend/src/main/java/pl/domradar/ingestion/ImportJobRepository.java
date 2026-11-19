package pl.domradar.ingestion;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {

    boolean existsBySourceIdAndStatusIn(Long sourceId, Collection<ImportStatus> statuses);

    long countBySourceIdAndStatus(Long sourceId, ImportStatus status);

    Optional<ImportJob> findTopBySourceIdOrderByCreatedAtDesc(Long sourceId);

    List<ImportJob> findByStatusAndCreatedAtBefore(ImportStatus status, Instant before);

    List<ImportJob> findByStatusAndStartedAtBefore(ImportStatus status, Instant before);

    @EntityGraph(attributePaths = "source")
    @Query("select j from ImportJob j where j.id = :id")
    Optional<ImportJob> findWithSourceById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ImportJob j where j.id = :id")
    Optional<ImportJob> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "source")
    @Query("""
            select j from ImportJob j
            where (:sourceId is null or j.source.id = :sourceId)
              and (:status is null or j.status = :status)
              and j.createdAt >= :from
              and j.createdAt <= :to
            """)
    Page<ImportJob> search(@Param("sourceId") Long sourceId, @Param("status") ImportStatus status,
                           @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    /**
     * Atomowe zarejestrowanie przetworzonego batcha (brak lost update przy wielu konsumentach).
     * Warunek na run_token odrzuca batche z wcześniejszych (ponowionych) prób workera.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE import_job SET processed_batches = processed_batches + 1,
                   created = created + :created, updated = updated + :updated,
                   merged = merged + :merged, errors = errors + :errors
            WHERE id = :id AND run_token = :token AND status = 'RUNNING'
            """, nativeQuery = true)
    int registerBatch(@Param("id") Long id, @Param("token") String token, @Param("created") int created,
                      @Param("updated") int updated, @Param("merged") int merged, @Param("errors") int errors);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE import_job SET processed_batches = processed_batches + 1, failed_batches = failed_batches + 1,
                   errors = errors + :size
            WHERE id = :id AND run_token = :token AND status = 'RUNNING'
            """, nativeQuery = true)
    int registerFailedBatch(@Param("id") Long id, @Param("token") String token, @Param("size") int size);
}
