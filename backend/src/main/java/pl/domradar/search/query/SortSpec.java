package pl.domradar.search.query;

import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sortowanie wielokluczowe, np. {@code sort=pricePerM2,asc&sort=area,desc}.
 * Parser jest odporny na to, że Spring rozbija pojedynczą wartość "price,asc" na dwa elementy listy.
 */
public record SortSpec(List<Order> orders) {

    public record Order(SortField field, boolean ascending) {

        public String toApi() {
            return field.apiName() + "," + (ascending ? "asc" : "desc");
        }
    }

    private static final int MAX_KEYS = 4;

    public static SortSpec parse(List<String> tokens) {
        List<String> flat = new ArrayList<>();
        if (tokens != null) {
            for (String t : tokens) {
                if (t == null) {
                    continue;
                }
                for (String part : t.split(",")) {
                    if (!part.isBlank()) {
                        flat.add(part.trim());
                    }
                }
            }
        }
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < flat.size(); i++) {
            String name = flat.get(i);
            SortField field = SortField.fromApiName(name).orElseThrow(() -> new DomainException(ErrorCode.VALIDATION_FAILED,
                    "Nieznane pole sortowania: " + name + ". Dozwolone: " + SortField.allowedNames(),
                    Map.of("field", "sort")));
            String direction = field.defaultDirection();
            if (i + 1 < flat.size() && isDirection(flat.get(i + 1))) {
                direction = flat.get(++i).toLowerCase(Locale.ROOT);
            }
            if (orders.stream().noneMatch(o -> o.field() == field)) {
                orders.add(new Order(field, direction.equals("asc")));
            }
        }
        if (orders.size() > MAX_KEYS) {
            orders = orders.subList(0, MAX_KEYS);
        }
        return new SortSpec(List.copyOf(orders));
    }

    private static boolean isDirection(String s) {
        return s.equalsIgnoreCase("asc") || s.equalsIgnoreCase("desc");
    }

    public boolean uses(SortField field) {
        return orders.stream().anyMatch(o -> o.field() == field);
    }

    /** Klauzula ORDER BY; zawsze NULLS LAST i stabilizujący klucz p.id. */
    public String toSql(boolean hasRelevance, boolean hasDistance) {
        List<String> parts = new ArrayList<>();
        for (Order o : orders) {
            if (o.field() == SortField.RELEVANCE && !hasRelevance) {
                continue;
            }
            if (o.field() == SortField.DISTANCE && !hasDistance) {
                continue;
            }
            boolean asc = o.field().invert() != o.ascending();
            parts.add(o.field().sql() + (asc ? " ASC" : " DESC") + " NULLS LAST");
        }
        if (parts.isEmpty()) {
            parts.add(hasRelevance ? "relevance DESC" : "p.first_seen_at DESC");
        }
        parts.add("p.id DESC");
        return String.join(", ", parts);
    }

    public List<String> toApi() {
        return orders.stream().map(Order::toApi).toList();
    }
}
