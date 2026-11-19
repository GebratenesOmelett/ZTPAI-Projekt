package pl.domradar.search.query;

import org.junit.jupiter.api.Test;
import pl.domradar.common.exception.DomainException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SortSpecTest {

    @Test
    void parsesRepeatedParametersAndSplitValues() {
        SortSpec repeated = SortSpec.parse(List.of("pricePerM2,asc", "area,desc"));
        SortSpec split = SortSpec.parse(List.of("pricePerM2", "asc", "area", "desc"));

        assertThat(repeated.toApi()).containsExactly("pricePerM2,asc", "area,desc");
        assertThat(split.toApi()).isEqualTo(repeated.toApi());
        assertThat(repeated.toSql(false, false))
                .isEqualTo("p.price_per_m2 ASC NULLS LAST, p.area DESC NULLS LAST, p.id DESC");
    }

    @Test
    void usesDefaultDirectionAndIgnoresDuplicates() {
        SortSpec spec = SortSpec.parse(List.of("price", "price,desc", "addedAt"));

        assertThat(spec.toApi()).containsExactly("price,asc", "addedAt,desc");
    }

    @Test
    void daysOnMarketAscendingMeansNewestFirst() {
        assertThat(SortSpec.parse(List.of("daysOnMarket,asc")).toSql(false, false)).startsWith("p.first_seen_at DESC");
    }

    @Test
    void unknownFieldIsRejectedAndSqlInjectionImpossible() {
        assertThatThrownBy(() -> SortSpec.parse(List.of("price; drop table property")))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void relevanceAndDistanceAreSkippedWhenNotApplicable() {
        SortSpec spec = SortSpec.parse(List.of("relevance", "distance"));

        assertThat(spec.toSql(false, false)).isEqualTo("p.first_seen_at DESC, p.id DESC");
        assertThat(spec.toSql(true, true)).isEqualTo("relevance DESC NULLS LAST, distance_m ASC NULLS LAST, p.id DESC");
    }
}
