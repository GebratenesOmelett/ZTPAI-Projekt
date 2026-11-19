package pl.domradar.search.query;

import org.junit.jupiter.api.Test;
import pl.domradar.property.PropertyType;
import pl.domradar.search.FeatureMode;
import pl.domradar.search.GeoPolygon;
import pl.domradar.search.SearchCriteria;
import pl.domradar.search.query.SearchSqlBuilder.Dimension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchSqlBuilderTest {

    @Test
    void emptyCriteriaShowOnlyActiveProperties() {
        assertThat(SearchSqlBuilder.from(SearchCriteria.empty()).where()).isEqualTo("p.status = 'ACTIVE'");
        assertThat(SearchSqlBuilder.from(SearchCriteria.builder().includeArchived(true).build()).where()).isEqualTo("TRUE");
    }

    @Test
    void valuesAreBoundAsParametersNotConcatenated() {
        SearchCriteria c = SearchCriteria.builder().q("balkon' OR 1=1 --").street("Puławska").build();
        SearchSqlBuilder b = SearchSqlBuilder.from(c);

        assertThat(b.where()).doesNotContain("OR 1=1").doesNotContain("Puławska");
        assertThat(b.params().getValues().values()).anySatisfy(v -> assertThat(String.valueOf(v)).contains("pulawsk"));
    }

    @Test
    void textQuerySupportsExclusionsAndTypoTolerance() {
        SearchSqlBuilder b = SearchSqlBuilder.from(SearchCriteria.builder().q("balkon -parter -suterena").build());

        assertThat(b.where()).contains("to_tsquery('domradar_pl'").contains("<% p.search_text").contains("NOT (p.search_vector @@");
        assertThat(b.params().getValues().values()).contains("balko:*", "parte:*", "sutere:*");
        assertThat(b.hasRelevance()).isTrue();
    }

    @Test
    void facetDimensionExcludesOnlyItsOwnFilter() {
        SearchCriteria c = SearchCriteria.builder().districtIds(List.of(1L, 2L)).rooms(List.of(2, 3))
                .propertyTypes(List.of(PropertyType.APARTMENT)).priceMax(new BigDecimal("900000")).build();
        SearchSqlBuilder b = SearchSqlBuilder.from(c);

        assertThat(b.where()).contains("p.district_id IN").contains("p.rooms IN").contains("p.current_price <=");
        assertThat(b.where(Dimension.DISTRICT)).doesNotContain("p.district_id IN").contains("p.rooms IN");
        assertThat(b.where(Dimension.ROOMS)).doesNotContain("p.rooms IN").contains("p.district_id IN");
        assertThat(b.where(Dimension.PRICE)).doesNotContain("p.current_price").contains("p.property_type IN");
    }

    @Test
    void fivePlusRoomsAndFloorRules() {
        SearchSqlBuilder b = SearchSqlBuilder.from(SearchCriteria.builder().rooms(List.of(1, 5))
                .notGroundFloor(true).notLastFloor(true).build());

        assertThat(b.where()).contains("p.rooms >= 5").contains("p.floor > 0").contains("p.floor < p.total_floors");
    }

    @Test
    void featuresAllVersusAny() {
        String all = SearchSqlBuilder.from(SearchCriteria.builder().features(List.of("balcony", "ELEVATOR")).build()).where();
        String any = SearchSqlBuilder.from(SearchCriteria.builder().features(List.of("BALCONY", "ELEVATOR"))
                .featureMode(FeatureMode.ANY).build()).where();

        assertThat(all).contains("count(DISTINCT f.code)");
        assertThat(any).contains("EXISTS (SELECT 1 FROM property_feature");
    }

    @Test
    void geographicFiltersUsePostgis() {
        GeoPolygon polygon = new GeoPolygon("Polygon", List.of(List.of(
                List.of(21.0, 52.2), List.of(21.05, 52.2), List.of(21.05, 52.25), List.of(21.0, 52.25))));
        SearchSqlBuilder b = SearchSqlBuilder.from(SearchCriteria.builder()
                .bbox(new pl.domradar.search.BoundingBox(52.1, 20.9, 52.3, 21.2))
                .lat(52.23).lon(21.01).radiusKm(3.0).polygon(polygon).build());

        assertThat(b.where()).contains("ST_MakeEnvelope").contains("ST_DWithin").contains("ST_GeogFromText");
        assertThat(b.params().getValues().values()).contains(3000.0)
                .anySatisfy(v -> assertThat(String.valueOf(v)).startsWith("SRID=4326;POLYGON((21.0000000 52.2000000"));
        assertThat(b.hasDistance()).isTrue();
    }
}
