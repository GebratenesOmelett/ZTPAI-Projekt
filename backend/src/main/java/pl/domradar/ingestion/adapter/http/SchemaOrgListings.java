package pl.domradar.ingestion.adapter.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import pl.domradar.common.util.TextNormalizer;
import pl.domradar.ingestion.adapter.RawListing;
import pl.domradar.ingestion.normalize.DictionaryLookup;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wyciąga ogłoszenia z danych schema.org (JSON-LD), które wiele portali osadza na stronach wyników:
 * {@code Offer} z {@code itemOffered: Accommodation} (Morizon, Gratka, Nieruchomości-online) albo
 * {@code RealEstateListing} z {@code offers} (Domiporta). Dane strukturalne są stabilniejsze od układu HTML.
 */
public final class SchemaOrgListings {

    private static final Pattern AREA_IN_TEXT = Pattern.compile("(\\d{1,4}(?:[.,]\\d{1,2})?)\\s?m(?:²|2|kw)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG_NUMBER = Pattern.compile("(\\d{5,})");
    private static final Pattern PRIVATE_HINT = Pattern.compile("prywatn|bez pośrednik|bez posrednik|bez prowizji|od właściciela|od wlasciciela",
            Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;
    private final DictionaryLookup dictionary;

    public SchemaOrgListings(ObjectMapper objectMapper, DictionaryLookup dictionary) {
        this.objectMapper = objectMapper;
        this.dictionary = dictionary;
    }

    /** Wszystkie obiekty ofert z bloków {@code application/ld+json} strony. */
    public List<JsonNode> extractOffers(String html) {
        Document doc = Jsoup.parse(html);
        List<JsonNode> offers = new ArrayList<>();
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                collect(objectMapper.readTree(script.data()), offers);
            } catch (Exception ignored) {
                // pojedynczy niepoprawny blok JSON-LD nie przerywa parsowania strony
            }
        }
        return offers;
    }

    private static void collect(JsonNode node, List<JsonNode> out) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            node.forEach(n -> collect(n, out));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        Set<String> types = types(node);
        if ((types.contains("Offer") && node.has("itemOffered")) || types.contains("RealEstateListing")) {
            out.add(node);
            return;
        }
        node.fields().forEachRemaining(e -> {
            if (e.getValue().isContainerNode()) {
                collect(e.getValue(), out);
            }
        });
    }

    private static Set<String> types(JsonNode node) {
        Set<String> types = new HashSet<>();
        JsonNode t = node.path("@type");
        if (t.isArray()) {
            t.forEach(x -> types.add(x.asText()));
        } else if (t.isTextual()) {
            types.add(t.asText());
        }
        return types;
    }

    /**
     * Mapuje ofertę schema.org na surowe ogłoszenie dla danego miasta wyszukiwania.
     *
     * @param localityIsDistrict true, gdy portal wpisuje w {@code addressLocality} dzielnicę/osiedle (Morizon, Gratka);
     *                           false, gdy miejscowość (wtedy oferty z innych miejscowości są pomijane)
     * @return empty, gdy brak ceny/metrażu lub ogłoszenie dotyczy innej miejscowości niż wyszukiwane miasto
     */
    public Optional<RawListing> toRaw(JsonNode node, PortalCity city, boolean rent, boolean localityIsDistrict) {
        JsonNode offer = node.has("offers") ? node.path("offers") : node;
        if (offer.isArray()) {
            offer = offer.path(0);
        }
        JsonNode item = offer.has("itemOffered") ? offer.path("itemOffered") : node.path("itemOffered");
        String url = text(node.path("url")).orElse(text(offer.path("url")).orElse(null));
        String name = text(node.path("name")).orElse(text(offer.path("name")).orElse(""));
        String price = text(offer.path("price")).filter(p -> !p.isBlank() && !p.equals("0")).orElse(null);
        if (url == null || price == null) {
            return Optional.empty();
        }
        String area = text(item.path("floorSize").path("value")).filter(a -> !a.isBlank()).orElseGet(() -> areaFromText(name));
        if (area == null) {
            return Optional.empty();
        }

        JsonNode address = item.path("address");
        String locality = text(address.path("addressLocality")).orElse(null);
        String street = text(address.path("streetAddress")).orElse(null);
        String district = null;
        if (locality != null && !TextNormalizer.key(locality).equals(TextNormalizer.key(city.name()))) {
            Optional<DictionaryLookup.CityRef> cityRef = dictionary.city(city.name());
            boolean knownDistrict = cityRef.isPresent() && dictionary.district(cityRef.get().id(), locality).isPresent();
            if (!localityIsDistrict && !knownDistrict && !locality.contains(city.name())) {
                // inna miejscowość (np. podmiejska) niż wyszukiwane miasto
                return Optional.empty();
            }
            district = locality;
        } else {
            district = name; // np. "Mieszkanie ...: Warszawa Białołęka: Białołęcka" — dopasowanie dzielnicy po tekście
        }

        String description = text(item.path("description")).orElse(text(node.path("description")).orElse(null));
        String seller = text(offer.path("seller").path("name")).orElse(text(node.path("seller").path("name")).orElse(null));
        String context = name + " " + Objects.toString(description, "");
        boolean privateSeller = seller == null && PRIVATE_HINT.matcher(context).find();
        return Optional.of(RawListing.builder()
                .externalId(externalId(url))
                .url(url)
                .title(name.trim())
                .description(description)
                .priceText(price)
                .currency(text(offer.path("priceCurrency")).orElse("PLN"))
                .areaText(area)
                .roomsText(rooms(item.path("numberOfRooms")))
                .floorText(text(item.path("floorLevel")).orElse(null))
                .propertyTypeText("mieszkanie")
                .transactionText(transaction(name, url, rent))
                .city(city.name())
                .district(district)
                .street(street)
                .features(List.of())
                .photos(images(node.has("image") ? node.path("image") : offer.path("image")))
                .agencyName(seller)
                .sellerText(privateSeller ? "prywatny" : "biuro")
                .build());
    }

    static String transaction(String name, String url, boolean rentContext) {
        String t = TextNormalizer.fold(name + " " + url);
        if (t.contains("wynaj") || t.contains("najem")) {
            return "wynajem";
        }
        if (t.contains("sprzeda")) {
            return "sprzedaż";
        }
        return rentContext ? "wynajem" : "sprzedaż";
    }

    static String externalId(String url) {
        String path = url.replaceAll("[?#].*$", "").replaceAll("/+$", "");
        Matcher m = LONG_NUMBER.matcher(path);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        if (last != null) {
            return last;
        }
        String segment = path.substring(path.lastIndexOf('/') + 1);
        return segment.length() > 100 ? segment.substring(segment.length() - 100) : segment;
    }

    static String areaFromText(String text) {
        Matcher m = AREA_IN_TEXT.matcher(text == null ? "" : text);
        return m.find() ? m.group(1) : null;
    }

    private static String rooms(JsonNode node) {
        if (node.isNumber() || node.isTextual()) {
            return node.asText();
        }
        if (node.has("value")) {
            return node.path("value").asText(null);
        }
        return null;
    }

    private static List<String> images(JsonNode node) {
        List<String> out = new ArrayList<>();
        if (node.isTextual()) {
            out.add(node.asText());
        } else if (node.isArray()) {
            node.forEach(n -> text(n.isObject() ? n.path("url") : n).ifPresent(out::add));
        } else if (node.isObject()) {
            text(node.path("url")).ifPresent(out::add);
        }
        return out.stream().filter(u -> u.startsWith("http")).limit(10).toList();
    }

    private static Optional<String> text(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Optional.empty();
        }
        String value = node.isValueNode() ? node.asText() : null;
        return value == null ? Optional.empty() : Optional.of(value.trim());
    }
}
