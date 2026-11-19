package pl.domradar.ingestion;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {

    boolean existsBySourceIdAndStatusIn(Long sourceId, Collection<ImportStatus> statuses);

    long countBySourceIdAndStatus(Long sourceId, ImportStatus status);

    @EntityGraph(attributePaths = "source")
    @Query("select j from ImportJob j where j.id = :id")
    Optional<ImportJob> findWithSourceById(@Param("id") Long id);

    @EntityGraph(attributePaths = "source")
    @Query("""
            select j from ImportJob j
            where (:sourceId is null or j.source.id = :sourceId)
              and (:status is null or j.status = :status)
              and (:from is null or j.createdAt >= :from)
              and (:to is null or j.createdAt <= :to)
            """)
    Page<ImportJob> search(@Param("sourceId") Long sourceId, @Param("status") ImportStatus status,
                           @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    /** Atomowe zwiększenie licznika przetworzonych batchy (wiele konsumentów, brak lost update). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE import_job SET processed_batches = processed_batches + 1,
                   created = created + :created, updated = updated + :updated,
                   merged = merged + :merged, errors = errors + :errors
            WHERE id = :id
            """, nativeQuery = true)
    int registerBatch(@Param("id") Long id, @Param("created") int created, @Param("updated") int updated,
                      @Param("merged") int merged, @Param("errors") int errors);
}
