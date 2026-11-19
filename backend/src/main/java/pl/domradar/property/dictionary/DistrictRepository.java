package pl.domradar.property.dictionary;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DistrictRepository extends JpaRepository<District, Long> {

    @EntityGraph(attributePaths = "city")
    List<District> findAllByOrderByCityNameAscNameAsc();

    @EntityGraph(attributePaths = "city")
    List<District> findByCityIdOrderByNameAsc(Long cityId);

    @EntityGraph(attributePaths = "city")
    List<District> findAllByOrderByIdAsc();
}
