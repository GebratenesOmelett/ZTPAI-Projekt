package pl.domradar.search.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import pl.domradar.search.BoundingBox;
import pl.domradar.search.GeoPolygon;
import pl.domradar.search.SearchCriteria;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SearchCriteriaValidatorTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static List<String> fields(Set<ConstraintViolation<SearchCriteria>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).toList();
    }

    private static GeoPolygon square() {
        return new GeoPolygon("Polygon", List.of(List.of(
                List.of(21.0, 52.2), List.of(21.1, 52.2), List.of(21.1, 52.3), List.of(21.0, 52.3), List.of(21.0, 52.2))));
    }

    @Test
    void validCriteriaPass() {
        SearchCriteria c = SearchCriteria.builder().priceMin(new BigDecimal("100")).priceMax(new BigDecimal("200"))
                .lat(52.2).lon(21.0).radiusKm(10.0).polygon(square()).build();

        assertThat(validator.validate(c)).isEmpty();
    }

    @Test
    void minGreaterThanMaxIsRejectedForEveryRange() {
        SearchCriteria c = SearchCriteria.builder()
                .priceMin(new BigDecimal("500")).priceMax(new BigDecimal("100"))
                .areaMin(new BigDecimal("80")).areaMax(new BigDecimal("40"))
                .floorMin(5).floorMax(2)
                .yearBuiltMin(2020).yearBuiltMax(1990)
                .build();

        assertThat(fields(validator.validate(c))).contains("priceMin", "areaMin", "floorMin", "yearBuiltMin");
    }

    @Test
    void radiusAbove50kmOrWithoutPointIsRejected() {
        assertThat(fields(validator.validate(SearchCriteria.builder().lat(52.2).lon(21.0).radiusKm(51.0).build())))
                .contains("radiusKm");
        assertThat(fields(validator.validate(SearchCriteria.builder().radiusKm(5.0).build()))).contains("radiusKm");
        assertThat(fields(validator.validate(SearchCriteria.builder().lat(52.2).build()))).contains("lon");
    }

    @Test
    void polygonWithMoreThan100PointsIsRejected() {
        List<List<Double>> ring = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            double angle = 2 * Math.PI * i / 101;
            ring.add(List.of(21.0 + 0.05 * Math.cos(angle), 52.2 + 0.05 * Math.sin(angle)));
        }
        SearchCriteria c = SearchCriteria.builder().polygon(new GeoPolygon("Polygon", List.of(ring))).build();

        assertThat(SearchCriteriaValidator.findViolations(c))
                .anySatisfy(v -> assertThat(v.message()).contains("100"));
    }

    @Test
    void selfIntersectingOrDegeneratePolygonIsRejected() {
        GeoPolygon bowTie = new GeoPolygon("Polygon", List.of(List.of(
                List.of(21.0, 52.2), List.of(21.1, 52.3), List.of(21.1, 52.2), List.of(21.0, 52.3))));
        GeoPolygon twoPoints = new GeoPolygon("Polygon", List.of(List.of(List.of(21.0, 52.2), List.of(21.1, 52.3))));
        GeoPolygon wrongType = new GeoPolygon("Point", List.of());

        assertThat(fields(validator.validate(SearchCriteria.builder().polygon(bowTie).build()))).contains("polygon");
        assertThat(fields(validator.validate(SearchCriteria.builder().polygon(twoPoints).build()))).contains("polygon");
        assertThat(fields(validator.validate(SearchCriteria.builder().polygon(wrongType).build()))).contains("polygon");
    }

    @Test
    void invertedBoundingBoxIsRejected() {
        SearchCriteria c = SearchCriteria.builder().bbox(new BoundingBox(52.3, 21.0, 52.1, 21.1)).build();

        assertThat(fields(validator.validate(c))).contains("bbox");
    }
}
