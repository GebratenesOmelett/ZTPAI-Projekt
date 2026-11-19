package pl.domradar.property.dictionary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FeatureRepository extends JpaRepository<Feature, Long> {

    Optional<Feature> findByCode(String code);

    List<Feature> findByCodeIn(Collection<String> codes);

    List<Feature> findAllByOrderByNameAsc();
}
