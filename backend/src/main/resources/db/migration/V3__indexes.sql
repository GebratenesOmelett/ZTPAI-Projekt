-- =====================================================================
-- Indeksy wyszukiwarki
-- =====================================================================
-- Pełnotekstowe (tsvector) i tolerancja literówek (trigramy)
CREATE INDEX ix_property_search_vector ON property USING GIN (search_vector);
CREATE INDEX ix_property_search_text_trgm ON property USING GIN (search_text gin_trgm_ops);
CREATE INDEX ix_property_street_trgm ON property USING GIN (f_unaccent(lower(street)) gin_trgm_ops);

-- Geografia (bbox, promień, poligon, deduplikacja po odległości)
CREATE INDEX ix_property_location ON property USING GIST (location);

-- Złożone btree pod najczęstsze filtry i sortowania
CREATE INDEX ix_property_main_filter ON property (status, transaction_type, property_type, city_id, district_id);
CREATE INDEX ix_property_status_price ON property (status, current_price);
CREATE INDEX ix_property_status_ppm2 ON property (status, price_per_m2);
CREATE INDEX ix_property_status_area ON property (status, area);
CREATE INDEX ix_property_first_seen ON property (status, first_seen_at DESC);
CREATE INDEX ix_property_price_drop ON property (status, last_price_drop_at DESC);
CREATE INDEX ix_property_fingerprint ON property (fingerprint);

CREATE INDEX ix_property_feature_feature ON property_feature (feature_id, property_id);

-- =====================================================================
-- Oferty, historia, zdjęcia
-- =====================================================================
CREATE INDEX ix_offer_property ON offer (property_id);
CREATE INDEX ix_offer_source_active ON offer (source_id, active);
CREATE INDEX ix_price_history_offer ON price_history (offer_id, changed_at);
CREATE INDEX ix_photo_offer ON photo (offer_id, position);

-- =====================================================================
-- Importy: co najwyżej jeden aktywny (QUEUED/RUNNING) job na źródło.
-- Gwarancja na poziomie bazy (reguła biznesowa nr 4), niezależna od wyścigów w aplikacji.
-- =====================================================================
CREATE UNIQUE INDEX ux_import_job_one_active ON import_job (source_id) WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX ix_import_job_source_created ON import_job (source_id, created_at DESC);
CREATE INDEX ix_import_job_status ON import_job (status, created_at DESC);

-- =====================================================================
-- Funkcje użytkownika
-- =====================================================================
CREATE INDEX ix_saved_search_user ON saved_search (user_id);
CREATE INDEX ix_saved_search_alert ON saved_search (alert_enabled, alert_mode);
CREATE INDEX ix_notification_user ON notification (user_id, read, created_at DESC);
CREATE INDEX ix_refresh_token_user ON refresh_token (user_id);
CREATE INDEX ix_district_city ON district (city_id);
