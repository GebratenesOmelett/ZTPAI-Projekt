package pl.domradar.common.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalizacja tekstu: małe litery, bez polskich znaków diakrytycznych, pojedyncze spacje. */
public final class TextNormalizer {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private TextNormalizer() {
    }

    /** "Żoliborz, ul. Słowackiego" → "zoliborz, ul. slowackiego" */
    public static String fold(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT).replace('ł', 'l');
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return SPACES.matcher(MARKS.matcher(decomposed).replaceAll("")).replaceAll(" ").trim();
    }

    /** Klucz do porównań: tylko litery i cyfry rozdzielone pojedynczą spacją. "Praga-Południe" → "praga poludnie" */
    public static String key(String text) {
        return SPACES.matcher(NON_ALNUM.matcher(fold(text)).replaceAll(" ")).replaceAll(" ").trim();
    }
}
