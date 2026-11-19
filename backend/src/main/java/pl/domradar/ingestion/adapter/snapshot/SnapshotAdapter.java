package pl.domradar.ingestion.adapter.snapshot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.ingestion.IngestionProperties;
import pl.domradar.ingestion.adapter.ImportContext;
import pl.domradar.ingestion.adapter.RawListing;
import pl.domradar.ingestion.adapter.SourceAdapter;
import pl.domradar.source.Source;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Parsuje lokalnie zapisane strony/eksporty ogłoszeń z katalogu {@code data/snapshots/<kod-źródła>/}.
 * Obsługuje pliki *.json (tablica obiektów z polskimi nazwami pól) oraz *.html (jedno ogłoszenie na plik).
 * Pokazuje "prawdziwe" parsowanie i normalizację bez łączenia się z portalem.
 */
@Slf4j
@Component
public class SnapshotAdapter implements SourceAdapter {

    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAPS = new TypeReference<>() {
    };

    private final IngestionProperties properties;
    private final ObjectMapper objectMapper;

    public SnapshotAdapter(IngestionProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String code() {
        return "snapshot";
    }

    @Override
    public String description() {
        return "Parsowanie lokalnych plików JSON/HTML z katalogu data/snapshots/<kod-źródła>/";
    }

    @Override
    public void checkAvailable(Source source) {
        if (!properties.demoDataEnabled()) {
            throw new DomainException(ErrorCode.SOURCE_DISABLED, "Dane demonstracyjne są wyłączone (DEMO_DATA_ENABLED=false)");
        }
        if (!Files.isDirectory(directory(source.getCode()))) {
            throw new DomainException(ErrorCode.SOURCE_DISABLED,
                    "Brak katalogu ze snapshotami dla źródła " + source.getCode(),
                    Map.of("path", directory(source.getCode()).toString()));
        }
    }

    @Override
    public Stream<RawListing> fetch(ImportContext ctx) {
        Path dir = directory(ctx.sourceCode());
        if (!Files.isDirectory(dir)) {
            throw new IllegalStateException("Snapshot directory not found: " + dir.toAbsolutePath());
        }
        List<Path> files;
        try (Stream<Path> list = Files.list(dir)) {
            files = list.filter(Files::isRegularFile).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files.stream().flatMap(this::parseFile);
    }

    Path directory(String sourceCode) {
        return Path.of(properties.snapshotsDir()).resolve(sourceCode);
    }

    private Stream<RawListing> parseFile(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".json")) {
                return parseJson(Files.readString(file, StandardCharsets.UTF_8)).stream();
            }
            if (name.endsWith(".html") || name.endsWith(".htm")) {
                return Stream.of(parseHtml(Files.readString(file, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read snapshot " + file, e);
        }
        log.debug("Skipping unsupported snapshot file {}", file);
        return Stream.empty();
    }

    List<RawListing> parseJson(String json) throws IOException {
        List<Map<String, Object>> items = objectMapper.readValue(json, LIST_OF_MAPS);
        List<RawListing> out = new ArrayList<>(items.size());
        for (Map<String, Object> m : items) {
            out.add(RawListing.builder()
                    .externalId(str(m, "id"))
                    .url(str(m, "url"))
                    .title(str(m, "tytul"))
                    .description(str(m, "opis"))
                    .priceText(str(m, "cena"))
                    .currency(Optional.ofNullable(str(m, "waluta")).orElse("PLN"))
                    .areaText(str(m, "powierzchnia"))
                    .roomsText(str(m, "pokoje"))
                    .floorText(str(m, "pietro"))
                    .totalFloorsText(str(m, "liczba_pieter"))
                    .yearBuiltText(str(m, "rok_budowy"))
                    .propertyTypeText(str(m, "rodzaj"))
                    .transactionText(str(m, "transakcja"))
                    .marketText(str(m, "rynek"))
                    .city(str(m, "miasto"))
                    .district(str(m, "dzielnica"))
                    .street(str(m, "ulica"))
                    .lat(dbl(m, "lat"))
                    .lon(dbl(m, "lon"))
                    .features(strList(m, "udogodnienia"))
                    .photos(strList(m, "zdjecia"))
                    .agencyName(isPrivate(str(m, "ogloszeniodawca")) ? null : str(m, "ogloszeniodawca"))
                    .sellerText(str(m, "ogloszeniodawca"))
                    .build());
        }
        return out;
    }

    RawListing parseHtml(String html) {
        Document doc = Jsoup.parse(html);
        Element article = Optional.ofNullable(doc.selectFirst("article.listing")).orElse(doc.body());
        Map<String, String> params = new HashMap<>();
        for (Element li : article.select("ul.params li[data-param]")) {
            params.put(li.attr("data-param"), li.text());
        }
        Element location = article.selectFirst(".location");
        String seller = text(article, ".seller");
        return RawListing.builder()
                .externalId(article.attr("data-id"))
                .url(Optional.ofNullable(article.selectFirst("a.listing-url")).map(a -> a.attr("href")).orElse(null))
                .title(text(article, ".listing-title"))
                .description(text(article, ".description"))
                .priceText(text(article, ".price"))
                .currency("PLN")
                .areaText(params.get("powierzchnia"))
                .roomsText(params.get("pokoje"))
                .floorText(params.get("pietro"))
                .totalFloorsText(params.get("liczba_pieter"))
                .yearBuiltText(params.get("rok_budowy"))
                .propertyTypeText(params.get("rodzaj"))
                .transactionText(params.get("transakcja"))
                .marketText(params.get("rynek"))
                .city(text(article, ".location .city"))
                .district(text(article, ".location .district"))
                .street(text(article, ".location .street"))
                .lat(location == null ? null : parseDouble(location.attr("data-lat")))
                .lon(location == null ? null : parseDouble(location.attr("data-lon")))
                .features(article.select("ul.amenities li").eachText())
                .photos(article.select(".gallery img").eachAttr("src"))
                .agencyName(isPrivate(seller) ? null : seller)
                .sellerText(seller)
                .build();
    }

    private static boolean isPrivate(String seller) {
        return seller == null || seller.toLowerCase(Locale.ROOT).contains("prywat");
    }

    private static String text(Element root, String css) {
        Element el = root.selectFirst(css);
        return el == null ? null : el.text();
    }

    private static String str(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v == null ? null : v.toString();
    }

    private static Double dbl(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return v == null ? null : parseDouble(v.toString());
    }

    private static Double parseDouble(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        return v == null ? List.of() : List.of(v.toString());
    }
}
