package pl.domradar.property;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    Optional<Offer> findBySourceIdAndExternalId(Long sourceId, String externalId);

    @EntityGraph(attributePaths = {"source", "priceHistory"})
    List<Offer> findByPropertyIdOrderByPriceAsc(Long propertyId);

    List<Offer> findByPropertyId(Long propertyId);

    @EntityGraph(attributePaths = {"property"})
    List<Offer> findBySourceIdAndActiveTrue(Long sourceId);

    boolean existsBySourceId(Long sourceId);

    @EntityGraph(attributePaths = {"source", "property"})
    @Query("""
            select o from Offer o
            where (:sourceId is null or o.source.id = :sourceId)
              and (:propertyId is null or o.property.id = :propertyId)
              and (:active is null or o.active = :active)
            """)
    Page<Offer> adminSearch(@Param("sourceId") Long sourceId, @Param("propertyId") Long propertyId,
                            @Param("active") Boolean active, Pageable pageable);
}
