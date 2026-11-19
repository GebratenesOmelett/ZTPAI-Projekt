package pl.domradar.ingestion.normalize;

import java.util.regex.Pattern;

/**
 * Minimalizacja danych osobowych (RODO): z opisów ogłoszeń usuwane są numery telefonów i adresy e-mail.
 * Kontakt z ogłoszeniodawcą odbywa się przez link do oryginalnego ogłoszenia na portalu.
 */
public final class ContactRedactor {

    public static final String PLACEHOLDER = "[kontakt w ogłoszeniu na portalu]";

    private static final Pattern EMAIL = Pattern.compile("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[\\p{L}]{2,}");
    /** 9 cyfr (opcjonalnie z +48 / 0048), rozdzielanych spacjami, kropkami lub myślnikami, np. "601 234 567", "+48 22-123-45-67". */
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\p{N})(?:(?:\\+|00)\\s?48[\\s.-]?)?\\(?\\d{2,3}\\)?(?:[\\s.-]?\\d{2,3}){2,3}(?!\\p{N})");

    private ContactRedactor() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = EMAIL.matcher(text).replaceAll(PLACEHOLDER);
        var matcher = PHONE.matcher(result);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String digits = matcher.group().replaceAll("\\D", "");
            boolean phone = digits.length() == 9 || (digits.length() == 11 && digits.startsWith("48"))
                    || (digits.length() == 13 && digits.startsWith("0048"));
            matcher.appendReplacement(sb, phone ? PLACEHOLDER : java.util.regex.Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
