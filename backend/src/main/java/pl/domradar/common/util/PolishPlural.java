package pl.domradar.common.util;

/** Polska odmiana liczebników: 1 ogłoszenie, 2-4 ogłoszenia, 5+ ogłoszeń (12-14 → ogłoszeń). */
public final class PolishPlural {

    private PolishPlural() {
    }

    public static String of(long n, String one, String few, String many) {
        if (n == 1) {
            return one;
        }
        long lastTwo = Math.abs(n) % 100;
        long last = Math.abs(n) % 10;
        if (last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14)) {
            return few;
        }
        return many;
    }

    public static String count(long n, String one, String few, String many) {
        return n + " " + of(n, one, few, many);
    }
}
