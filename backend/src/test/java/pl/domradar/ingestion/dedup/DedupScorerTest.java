package pl.domradar.ingestion.dedup;

import org.junit.jupiter.api.Test;
import pl.domradar.ingestion.normalize.NormalizedListing;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DedupScorerTest {

    private static final int THRESHOLD = 70;

    private static NormalizedListing.NormalizedListingBuilder listing() {
        return NormalizedListing.builder()
                .externalId("X1").title("Słoneczne 3-pokojowe mieszkanie 62,5 m², Mokotów")
                .price(new BigDecimal("1200000")).propertyType(PropertyType.APARTMENT)
                .transactionType(TransactionType.SALE).cityId(1L).districtId(10L)
                .lat(52.1934).lon(21.0346).geocoded(false)
                .area(new BigDecimal("62.50")).rooms(3).floor(4).totalFloors(10)
                .featureCodes(Set.of()).photos(List.of());
    }

    private static PropertyCandidate candidate(BigDecimal area, Integer rooms, Integer floor, double lat, double lon,
                                               BigDecimal price, String title) {
        return new PropertyCandidate(100L, 1L, 10L, PropertyType.APARTMENT, TransactionType.SALE,
                area, rooms, floor, lat, lon, price, List.of(title));
    }

    @Test
    void sameApartmentFromAnotherPortalIsAMatch() {
        PropertyCandidate c = candidate(new BigDecimal("62.00"), 3, 4, 52.1937, 21.0349,
                new BigDecimal("1225000"), "Mieszkanie Warszawa, Mokotów, ul. Puławska, 62 m²");

        DedupScorer.Score score = DedupScorer.score(listing().build(), c);

        assertThat(score.total()).isGreaterThanOrEqualTo(THRESHOLD);
        assertThat(score.parts()).containsEntry("area", 20).containsEntry("distance", 20).containsEntry("price", 10);
    }

    @Test
    void differentCityTypeOrTransactionGivesZero() {
        PropertyCandidate otherCity = new PropertyCandidate(100L, 2L, 10L, PropertyType.APARTMENT, TransactionType.SALE,
                new BigDecimal("62.5"), 3, 4, 52.1934, 21.0346, new BigDecimal("1200000"), List.of("x"));
        PropertyCandidate rent = new PropertyCandidate(100L, 1L, 10L, PropertyType.APARTMENT, TransactionType.RENT,
                new BigDecimal("62.5"), 3, 4, 52.1934, 21.0346, new BigDecimal("1200000"), List.of("x"));

        assertThat(DedupScorer.score(listing().build(), otherCity).total()).isZero();
        assertThat(DedupScorer.score(listing().build(), rent).total()).isZero();
    }

    @Test
    void neighbourInSameBuildingWithDifferentFloorAndAreaIsNotMerged() {
        PropertyCandidate neighbour = candidate(new BigDecimal("74.00"), 3, 7, 52.1934, 21.0347,
                new BigDecimal("1390000"), "Słoneczne 3-pokojowe mieszkanie 74 m², Mokotów");

        DedupScorer.Score score = DedupScorer.score(listing().build(), neighbour);

        assertThat(score.total()).isLessThan(THRESHOLD);
        assertThat(score.parts()).containsEntry("floor", -25).containsEntry("area", 0);
    }

    @Test
    void identicalLayoutOnDifferentFloorIsNotMergedThanksToFloorPenalty() {
        PropertyCandidate sameLayout = candidate(new BigDecimal("62.50"), 3, 8, 52.1934, 21.0346,
                new BigDecimal("1200000"), "Słoneczne 3-pokojowe mieszkanie 62,5 m², Mokotów");

        assertThat(DedupScorer.score(listing().build(), sameLayout).total()).isLessThan(THRESHOLD);
    }

    @Test
    void areaOutsideTwoPercentToleranceLosesAreaPoints() {
        PropertyCandidate c = candidate(new BigDecimal("64.00"), 3, 4, 52.1934, 21.0346,
                new BigDecimal("1200000"), "inny tytuł");

        assertThat(DedupScorer.score(listing().build(), c).parts()).containsEntry("area", 0);
        assertThat(DedupScorer.withinTolerance(new BigDecimal("62.5"), new BigDecimal("63.7"), 0.02)).isTrue();
        assertThat(DedupScorer.withinTolerance(new BigDecimal("62.5"), new BigDecimal("63.9"), 0.02)).isFalse();
    }

    @Test
    void missingFloorAndGeocodedLocationGivePartialPoints() {
        NormalizedListing withoutFloor = listing().floor(null).geocoded(true).lat(52.20).lon(21.04).build();
        PropertyCandidate c = candidate(new BigDecimal("62.00"), 3, 4, 52.1934, 21.0346,
                new BigDecimal("1210000"), "Mieszkanie 3 pokoje Mokotów 62 m2");

        DedupScorer.Score score = DedupScorer.score(withoutFloor, c);

        assertThat(score.parts()).containsEntry("floor", 5).containsEntry("distance", 10);
        assertThat(score.total()).isGreaterThanOrEqualTo(THRESHOLD);
    }

    @Test
    void distantListingLosesDistancePoints() {
        PropertyCandidate far = candidate(new BigDecimal("62.50"), 3, 4, 52.2000, 21.0500,
                new BigDecimal("1200000"), "x");

        assertThat(DedupScorer.score(listing().build(), far).parts()).containsEntry("distance", 0);
        assertThat(DedupScorer.haversineMeters(52.1934, 21.0346, 52.1934, 21.0368)).isBetween(140.0, 160.0);
    }
}
