package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import pl.domradar.ingestion.normalize.TransactionClassifier;
import pl.domradar.property.PropertyType;
import pl.domradar.property.TransactionType;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Jednorazowa korekta danych zaimportowanych przed wprowadzeniem {@link TransactionClassifier}: nieruchomości
 * z ogłoszeń wystawionych w złej kategorii portalu (np. "sprzedaż" za 2500 zł) dostają właściwy rodzaj transakcji.
 * Zmiana następuje tylko wtedy, gdy ceny wszystkich ofert nieruchomości wskazują tę samą, przeciwną kategorię.
 * Aktualizowane są też atrybuty ofert, żeby deduplikacja widziała poprawny rodzaj transakcji.
 */
public class V13__fix_misclassified_transactions extends BaseJavaMigration {

    private record Candidate(long id, PropertyType type, TransactionType tx, BigDecimal area,
                             BigDecimal minPrice, BigDecimal maxPrice) {
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        List<Candidate> candidates = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT p.id, p.property_type, p.transaction_type, p.area,
                            min(o.price) AS min_price, max(o.price) AS max_price
                     FROM property p JOIN offer o ON o.property_id = p.id
                     WHERE p.property_type IN ('APARTMENT', 'HOUSE') AND p.area > 0
                     GROUP BY p.id""")) {
            while (rs.next()) {
                candidates.add(new Candidate(rs.getLong("id"), PropertyType.valueOf(rs.getString("property_type")),
                        TransactionType.valueOf(rs.getString("transaction_type")), rs.getBigDecimal("area"),
                        rs.getBigDecimal("min_price"), rs.getBigDecimal("max_price")));
            }
        }
        try (PreparedStatement updateProperty = connection.prepareStatement(
                "UPDATE property SET transaction_type = ? WHERE id = ?");
             PreparedStatement updateOffers = connection.prepareStatement(
                     "UPDATE offer SET attributes = jsonb_set(attributes, '{transactionType}', to_jsonb(?::text)) WHERE property_id = ?")) {
            for (Candidate c : candidates) {
                TransactionType byMin = TransactionClassifier.classify(c.tx(), c.type(), c.minPrice(), c.area());
                TransactionType byMax = TransactionClassifier.classify(c.tx(), c.type(), c.maxPrice(), c.area());
                if (byMin == c.tx() || byMin != byMax) {
                    continue;
                }
                updateProperty.setString(1, byMin.name());
                updateProperty.setLong(2, c.id());
                updateProperty.addBatch();
                updateOffers.setString(1, byMin.name());
                updateOffers.setLong(2, c.id());
                updateOffers.addBatch();
            }
            updateProperty.executeBatch();
            updateOffers.executeBatch();
        }
    }
}
