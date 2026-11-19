package pl.domradar.property;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import pl.domradar.common.util.TextNormalizer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** Pomocnicze funkcje tworzenia nieruchomości: punkt geograficzny (SRID 4326) i fingerprint. */
public final class PropertyFactory {

    private static final GeometryFactory GEOMETRY = new GeometryFactory(new PrecisionModel(), 4326);

    private PropertyFactory() {
    }

    public static Point point(double lat, double lon) {
        return GEOMETRY.createPoint(new Coordinate(lon, lat));
    }

    /**
     * Fingerprint = SHA-256 z kluczowych atrybutów (miasto, dzielnica, typ, transakcja, metraż zaokrąglony do m², pokoje,
     * piętro, ulica). Służy do szybkiej identyfikacji identycznych wpisów i diagnostyki; o scaleniu decyduje scoring.
     */
    public static String fingerprint(Long cityId, Long districtId, PropertyType type, TransactionType tx,
                                     BigDecimal area, Integer rooms, Integer floor, String street) {
        String key = String.join("|",
                Objects.toString(cityId), Objects.toString(districtId), type.name(), tx.name(),
                area == null ? "" : area.setScale(0, RoundingMode.HALF_UP).toPlainString(),
                Objects.toString(rooms), Objects.toString(floor), TextNormalizer.key(street));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
