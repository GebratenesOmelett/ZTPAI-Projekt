package pl.domradar.ingestion.dedup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TrigramSimilarityTest {

    @Test
    void matchesPgTrgmReferenceValue() {
        // przykład z dokumentacji PostgreSQL: similarity('word', 'two words') = 0.36363637
        assertThat(TrigramSimilarity.similarity("word", "two words")).isCloseTo(0.363636, within(1e-5));
    }

    @Test
    void identicalAndDisjointTexts() {
        assertThat(TrigramSimilarity.similarity("Mokotów", "mokotow")).isEqualTo(1.0);
        assertThat(TrigramSimilarity.similarity("abc", "xyz")).isZero();
        assertThat(TrigramSimilarity.similarity("", "abc")).isZero();
    }

    @Test
    void similarTitlesFromDifferentPortalsScoreHigherThanUnrelated() {
        double similar = TrigramSimilarity.similarity("3-pokojowe mieszkanie 62 m², Mokotów",
                "Mieszkanie 3 pokoje Mokotów 62 m2");
        double unrelated = TrigramSimilarity.similarity("3-pokojowe mieszkanie 62 m², Mokotów",
                "Dom wolnostojący z ogrodem Wawer");
        assertThat(similar).isGreaterThan(unrelated).isGreaterThan(0.3);
    }
}
