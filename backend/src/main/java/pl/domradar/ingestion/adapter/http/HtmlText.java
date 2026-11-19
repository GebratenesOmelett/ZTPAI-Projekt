package pl.domradar.ingestion.adapter.http;

import org.jsoup.nodes.Element;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Drobne pomocnicze operacje na tekście kart ogłoszeń parsowanych z HTML. */
final class HtmlText {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private HtmlText() {
    }

    /** Tekst elementu ze znormalizowanymi spacjami (w tym twardymi) albo null, gdy element nie istnieje. */
    static String text(Element element) {
        if (element == null) {
            return null;
        }
        String t = element.text().replace(' ', ' ').replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? null : t;
    }

    /** Pierwsza liczba w tekście ("39m2" → "39", "81.35 m2" → "81.35"). */
    static String firstNumber(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = NUMBER.matcher(text);
        return m.find() ? m.group() : null;
    }

    /** Ostatni ciąg cyfr w tekście ("offer-73652356" → "73652356"). */
    static String lastDigits(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = DIGITS.matcher(text);
        String last = null;
        while (m.find()) {
            last = m.group();
        }
        return last;
    }

    static String absolute(String base, String href) {
        if (href == null || href.isBlank()) {
            return null;
        }
        return href.startsWith("http") ? href : base + (href.startsWith("/") ? "" : "/") + href;
    }
}
