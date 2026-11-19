package pl.domradar.ingestion.dedup;

import pl.domradar.common.util.TextNormalizer;

import java.util.HashSet;
import java.util.Set;

/**
 * Podobieństwo trigramowe zgodne z algorytmem rozszerzenia pg_trgm:
 * każde słowo jest poprzedzone dwiema spacjami i zakończone jedną, a wynik to |A∩B| / |A∪B|.
 * Liczone w Javie, bo scoring deduplikacji jest czystą funkcją testowaną jednostkowo.
 */
public final class TrigramSimilarity {

    private TrigramSimilarity() {
    }

    public static double similarity(String a, String b) {
        Set<String> ta = trigrams(a);
        Set<String> tb = trigrams(b);
        if (ta.isEmpty() || tb.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(ta);
        intersection.retainAll(tb);
        int union = ta.size() + tb.size() - intersection.size();
        return (double) intersection.size() / union;
    }

    static Set<String> trigrams(String text) {
        Set<String> result = new HashSet<>();
        for (String word : TextNormalizer.key(text).split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String padded = "  " + word + " ";
            for (int i = 0; i + 3 <= padded.length(); i++) {
                result.add(padded.substring(i, i + 3));
            }
        }
        return result;
    }
}
