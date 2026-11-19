package pl.domradar.property;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    @EntityGraph(attributePaths = {"city", "district", "features"})
    @Query("select p from Property p where p.id = :id")
    Optional<Property> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"city", "district"})
    @Query("""
            select p from Property p
            where (:status is null or p.status = :status)
              and (:cityId is null or p.city.id = :cityId)
              and (:q is null or lower(p.title) like lower(concat('%', cast(:q as string), '%'))
                   or lower(p.street) like lower(concat('%', cast(:q as string), '%')))
            """)
    Page<Property> adminSearch(@Param("q") String q, @Param("status") PropertyStatus status,
                               @Param("cityId") Long cityId, Pageable pageable);

    @EntityGraph(attributePaths = {"city", "district"})
    List<Property> findByIdIn(java.util.Collection<Long> ids);

    List<Property> findByFingerprint(String fingerprint);

    long countByStatus(PropertyStatus status);
}
