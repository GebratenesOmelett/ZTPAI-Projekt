package pl.domradar.ingestion.normalize;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.common.util.TextNormalizer;
import pl.domradar.property.dictionary.City;
import pl.domradar.property.dictionary.CityRepository;
import pl.domradar.property.dictionary.District;
import pl.domradar.property.dictionary.DistrictRepository;

import java.util.*;

/**
 * Mapowanie nazw miast, dzielnic i cech z portali na słowniki aplikacji.
 * Słowniki są małe i niezmienne w czasie działania, więc trzymamy je w pamięci.
 */
@Component
public class DictionaryLookup {

    public record CityRef(Long id, String name, double lat, double lon) {
    }

    public record DistrictRef(Long id, Long cityId, String name, double lat, double lon) {
    }

    /** Słowa kluczowe (po normalizacji) → kod cechy. Kolejność ma znaczenie (pierwsze dopasowanie wygrywa). */
    private static final List<Map.Entry<String, String>> FEATURE_KEYWORDS = List.of(
            Map.entry("balkon", "BALCONY"), Map.entry("taras", "TERRACE"),
            Map.entry("ogrod", "GARDEN"), Map.entry("garaz", "GARAGE"), Map.entry("parking", "GARAGE"),
            Map.entry("winda", "ELEVATOR"), Map.entry("piwnic", "BASEMENT"), Map.entry("komork", "STORAGE"),
            Map.entry("klimatyz", "AIR_CONDITIONING"), Map.entry("umeblow", "FURNISHED"), Map.entry("meble", "FURNISHED"),
            Map.entry("monitoring", "MONITORING"), Map.entry("ochrona", "MONITORING"), Map.entry("loggi", "LOGGIA"),
            Map.entry("oddzielna kuchnia", "SEPARATE_KITCHEN"), Map.entry("osobna kuchnia", "SEPARATE_KITCHEN"));

    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;

    private volatile Map<String, CityRef> citiesByKey;
    private volatile Map<Long, Map<String, DistrictRef>> districtsByCity;

    public DictionaryLookup(CityRepository cityRepository, DistrictRepository districtRepository) {
        this.cityRepository = cityRepository;
        this.districtRepository = districtRepository;
    }

    public Optional<CityRef> city(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        ensureLoaded();
        for (String part : text.split("[,;(]")) {
            CityRef ref = citiesByKey.get(TextNormalizer.key(part));
            if (ref != null) {
                return Optional.of(ref);
            }
        }
        return Optional.empty();
    }

    public Optional<DistrictRef> district(Long cityId, String text) {
        if (cityId == null || text == null || text.isBlank()) {
            return Optional.empty();
        }
        ensureLoaded();
        Map<String, DistrictRef> map = districtsByCity.getOrDefault(cityId, Map.of());
        String key = TextNormalizer.key(text);
        DistrictRef exact = map.get(key);
        if (exact != null) {
            return Optional.of(exact);
        }
        for (String part : text.split("[,;/(]")) {
            DistrictRef ref = map.get(TextNormalizer.key(part));
            if (ref != null) {
                return Optional.of(ref);
            }
        }
        // np. "Warszawa Mokotów Służew" — najdłuższy klucz zawarty w tekście
        String padded = " " + key + " ";
        return map.entrySet().stream()
                .filter(e -> padded.contains(" " + e.getKey() + " "))
                .max(Comparator.comparingInt(e -> e.getKey().length()))
                .map(Map.Entry::getValue);
    }

    public Set<String> features(Collection<String> labels) {
        Set<String> codes = new TreeSet<>();
        if (labels == null) {
            return codes;
        }
        for (String label : labels) {
            if (label == null) {
                continue;
            }
            for (String part : label.split("[,;|]")) {
                String key = TextNormalizer.key(part);
                if (key.isEmpty() || key.startsWith("brak") || key.startsWith("bez ")) {
                    continue;
                }
                FEATURE_KEYWORDS.stream().filter(e -> key.contains(e.getKey())).findFirst()
                        .ifPresent(e -> codes.add(e.getValue()));
            }
        }
        return codes;
    }

    @Transactional(readOnly = true)
    public synchronized void reload() {
        Map<String, CityRef> cities = new HashMap<>();
        for (City c : cityRepository.findAll()) {
            CityRef ref = new CityRef(c.getId(), c.getName(), c.getLat(), c.getLon());
            cities.put(TextNormalizer.key(c.getName()), ref);
            cities.put(TextNormalizer.key(c.getSlug()), ref);
        }
        Map<Long, Map<String, DistrictRef>> districts = new HashMap<>();
        for (District d : districtRepository.findAllByOrderByIdAsc()) {
            Long cityId = d.getCity().getId();
            DistrictRef ref = new DistrictRef(d.getId(), cityId, d.getName(), d.getLat(), d.getLon());
            Map<String, DistrictRef> map = districts.computeIfAbsent(cityId, k -> new HashMap<>());
            map.put(TextNormalizer.key(d.getName()), ref);
            map.put(TextNormalizer.key(d.getSlug()), ref);
            for (String alias : d.aliasList()) {
                map.putIfAbsent(TextNormalizer.key(alias), ref);
            }
        }
        this.districtsByCity = districts;
        this.citiesByKey = cities;
    }

    private void ensureLoaded() {
        if (citiesByKey == null) {
            reload();
        }
    }
}
