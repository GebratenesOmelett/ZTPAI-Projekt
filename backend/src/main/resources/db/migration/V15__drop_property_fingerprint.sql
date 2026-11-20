-- Odcisk nieruchomości (SHA-256 kluczowych atrybutów) był zapisywany i indeksowany, ale nigdy nie był odczytywany —
-- o scaleniu ofert decyduje scoring deduplikacji. Usunięcie oszczędza liczenie skrótu i aktualizację indeksu
-- przy każdej nowej nieruchomości.
DROP INDEX IF EXISTS ix_property_fingerprint;
ALTER TABLE property DROP COLUMN IF EXISTS fingerprint;
