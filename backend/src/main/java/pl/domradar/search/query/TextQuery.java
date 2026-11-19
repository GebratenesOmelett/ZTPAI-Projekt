package pl.domradar.search.query;

import pl.domradar.common.util.TextNormalizer;

import java.util.ArrayList;
import java.util.List;

/**
 * Zapytanie tekstowe rozbite na słowa wymagane i wykluczone ("balkon metro -parter -suterena").
 * Ponieważ PostgreSQL nie ma polskiego stemmera, stosujemy lekki stemming: dla dłuższych słów obcinamy
 * końcówkę fleksyjną i dopasowujemy prefiksowo (np. "suterena" → "sutere:*" pasuje do "suterenie").
 */
public record TextQuery(List<String> include, List<String> exclude) {

    private static final int MAX_TERMS = 10;

    public static TextQuery parse(String q) {
        List<String> include = new ArrayList<>();
        List<String> exclude = new ArrayList<>();
        if (q == null || q.isBlank()) {
            return new TextQuery(include, exclude);
        }
        for (String token : q.trim().split("\\s+")) {
            boolean negative = token.startsWith("-") || token.startsWith("!");
            String term = TextNormalizer.key(negative ? token.substring(1) : token).replace(" ", "");
            if (term.isEmpty() || include.size() + exclude.size() >= MAX_TERMS) {
                continue;
            }
            (negative ? exclude : include).add(term);
        }
        return new TextQuery(include, exclude);
    }

    public boolean isEmpty() {
        return include.isEmpty() && exclude.isEmpty();
    }

    public boolean hasPositiveTerms() {
        return !include.isEmpty();
    }

    /** Prefiks do to_tsquery: znormalizowany, bez znaków specjalnych, z obciętą końcówką. */
    public static String prefixQuery(String term) {
        return stem(term) + ":*";
    }

    static String stem(String term) {
        int len = term.length();
        if (len >= 7) {
            return term.substring(0, len - 2);
        }
        if (len >= 5) {
            return term.substring(0, len - 1);
        }
        return term;
    }

    /** Zapytanie OR do rankingu trafności. */
    public String rankQuery() {
        return String.join(" | ", include.stream().map(TextQuery::prefixQuery).toList());
    }

    public String positiveText() {
        return String.join(" ", include);
    }
}
