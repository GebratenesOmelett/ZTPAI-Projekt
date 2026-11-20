package pl.domradar.ingestion.normalize;

import pl.domradar.common.util.TextNormalizer;
import pl.domradar.property.MarketType;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsery wartości w formatach spotykanych na polskich portalach:
 * "649 000 zł", "649000,00 PLN", "€ 650 000", "54,3 m²", "54.30 m2", "3 pokoje", "Kawalerka",
 * "parter/4", "3 piętro", "2008 r." itd.
 */
public final class ValueParsers {

    private static final Pattern NUMBER = Pattern.compile("\\d[\\d\\s.,]*");
    private static final Pattern INTEGER = Pattern.compile("-?\\d+");
    private static final Pattern YEAR = Pattern.compile("(18|19|20)\\d{2}");
    /** Metraż zapisany w tekście, np. "42 m²", "51,58m²", "47 m2", "60mkw". */
    // bez końcówek liczb grupowanych spacją ("3 138 m²" to nie 138 m²); spacje także twarde (U+00A0)
    private static final Pattern AREA_IN_TEXT = Pattern.compile(
            "(?<![\\d.,])(?<!\\d[\\s\\u00a0])(\\d{1,4}(?:[.,]\\d{1,2})?)[\\s\\u00a0]?(?:m²|m2|mkw|m\\.?\\s?kw)",
            Pattern.CASE_INSENSITIVE);

    private ValueParsers() {
    }

    public record FloorInfo(Integer floor, Integer totalFloors) {
    }

    /** Pierwszy metraż zapisany w tekście (np. w tytule ogłoszenia). */
    public static Optional<BigDecimal> areaInText(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = AREA_IN_TEXT.matcher(text);
        return m.find() ? Optional.of(new BigDecimal(m.group(1).replace(',', '.'))) : Optional.empty();
    }

    /** Pierwsza liczba w tekście, z rozpoznaniem separatora tysięcy i części dziesiętnej. */
    public static Optional<BigDecimal> parseAmount(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = NUMBER.matcher(text.replace(' ', ' ').replace(' ', ' '));
        if (!m.find()) {
            return Optional.empty();
        }
        String num = m.group().replaceAll("\\s", "");
        num = num.replaceAll("[.,]+$", "");
        if (num.isEmpty()) {
            return Optional.empty();
        }
        int sep = Math.max(num.lastIndexOf(','), num.lastIndexOf('.'));
        String integerPart;
        String fraction = "";
        int digitsAfter = sep < 0 ? 0 : num.length() - sep - 1;
        if (sep >= 0 && digitsAfter >= 1 && digitsAfter <= 2) {
            integerPart = num.substring(0, sep).replaceAll("[.,]", "");
            fraction = num.substring(sep + 1);
        } else {
            integerPart = num.replaceAll("[.,]", "");
        }
        if (integerPart.isEmpty()) {
            integerPart = "0";
        }
        return Optional.of(new BigDecimal(fraction.isEmpty() ? integerPart : integerPart + "." + fraction));
    }

    public static Optional<Integer> parseRooms(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String folded = TextNormalizer.fold(text);
        if (folded.contains("kawalerk")) {
            return Optional.of(1);
        }
        return firstInt(folded).filter(r -> r >= 1 && r <= 20);
    }

    public static FloorInfo parseFloor(String floorText, String totalFloorsText) {
        Integer floor = null;
        Integer total = null;
        if (floorText != null && !floorText.isBlank()) {
            String folded = TextNormalizer.fold(floorText);
            String floorPart = folded;
            int slash = folded.indexOf('/');
            if (slash >= 0) {
                floorPart = folded.substring(0, slash);
                total = firstInt(folded.substring(slash + 1)).orElse(null);
            }
            if (floorPart.contains("parter")) {
                floor = 0;
            } else if (floorPart.contains("suteren")) {
                floor = -1;
            } else {
                floor = firstInt(floorPart).orElse(null);
            }
        }
        if (totalFloorsText != null && !totalFloorsText.isBlank()) {
            total = firstInt(TextNormalizer.fold(totalFloorsText)).orElse(total);
        }
        if (floor != null && (floor < -1 || floor > 100)) {
            floor = null;
        }
        if (total != null && (total < 0 || total > 100)) {
            total = null;
        }
        return new FloorInfo(floor, total);
    }

    public static Optional<Integer> parseYear(String text) {
        if (text == null) {
            return Optional.empty();
        }
        Matcher m = YEAR.matcher(text);
        return m.find() ? Optional.of(Integer.parseInt(m.group())) : Optional.empty();
    }

    public static Optional<PropertyType> parsePropertyType(String text) {
        String t = TextNormalizer.fold(text);
        if (t.isEmpty()) {
            return Optional.empty();
        }
        if (t.contains("lokal") || t.contains("uzytkow") || t.contains("biur") || t.contains("komercyj")) {
            return Optional.of(PropertyType.COMMERCIAL);
        }
        if (t.contains("dzialk") || t.contains("grunt")) {
            return Optional.of(PropertyType.PLOT);
        }
        if (t.matches(".*\\bdom\\b.*") || t.startsWith("dom") || t.contains("segment") || t.contains("blizniak")) {
            return Optional.of(PropertyType.HOUSE);
        }
        if (t.matches(".*\\bpokoj\\b.*") && !t.contains("mieszkan")) {
            return Optional.of(PropertyType.ROOM);
        }
        if (t.contains("mieszkan") || t.contains("kawalerk") || t.contains("apartament")) {
            return Optional.of(PropertyType.APARTMENT);
        }
        return Optional.empty();
    }

    public static Optional<TransactionType> parseTransaction(String text) {
        String t = TextNormalizer.fold(text);
        if (t.contains("wynaj") || t.contains("najem") || t.contains("/mc") || t.contains("mies")) {
            return Optional.of(TransactionType.RENT);
        }
        if (t.contains("sprzeda") || t.contains("sprzedam") || t.contains("na sprzedaz")) {
            return Optional.of(TransactionType.SALE);
        }
        return Optional.empty();
    }

    public static Optional<MarketType> parseMarket(String text) {
        String t = TextNormalizer.fold(text);
        if (t.contains("pierwot") || t.contains("deweloper")) {
            return Optional.of(MarketType.PRIMARY);
        }
        if (t.contains("wtorn")) {
            return Optional.of(MarketType.SECONDARY);
        }
        return Optional.empty();
    }

    /** true = osoba prywatna, false = biuro/firma, empty = brak informacji. */
    public static Optional<Boolean> parsePrivateSeller(String text) {
        String t = TextNormalizer.fold(text);
        if (t.isEmpty()) {
            return Optional.empty();
        }
        if (t.contains("prywat") || t.contains("wlasciciel") || t.contains("bez posrednik")) {
            return Optional.of(true);
        }
        if (t.contains("biur") || t.contains("agenc") || t.contains("firma") || t.contains("deweloper") || t.contains("posrednik")) {
            return Optional.of(false);
        }
        return Optional.empty();
    }

    public static boolean isEuro(String currency, String priceText) {
        String c = currency == null ? "" : currency.trim().toUpperCase();
        return c.equals("EUR") || c.equals("€") || (priceText != null && (priceText.contains("€") || priceText.toUpperCase().contains("EUR")));
    }

    private static Optional<Integer> firstInt(String text) {
        Matcher m = INTEGER.matcher(text);
        return m.find() ? Optional.of(Integer.parseInt(m.group())) : Optional.empty();
    }
}
