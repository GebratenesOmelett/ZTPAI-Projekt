-- Rozszerzenia PostgreSQL wykorzystywane przez DomRadar
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- unaccent() nie jest IMMUTABLE, więc nie da się go użyć w indeksie wyrażeniowym.
-- Opakowanie z jawnie podanym słownikiem jest bezpieczne i powszechnie stosowane.
CREATE OR REPLACE FUNCTION f_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, $1) $$;

-- Konfiguracja wyszukiwania pełnotekstowego dla języka polskiego.
-- PostgreSQL nie zawiera polskiego stemmera, dlatego używamy słownika "simple"
-- poprzedzonego "unaccent" (bez znaków diakrytycznych, małe litery).
-- Odmianę słów obsługujemy dopasowaniem prefiksowym (term:*), a literówki przez pg_trgm.
CREATE TEXT SEARCH CONFIGURATION domradar_pl (COPY = pg_catalog.simple);
ALTER TEXT SEARCH CONFIGURATION domradar_pl
    ALTER MAPPING FOR hword, hword_part, word, asciiword, asciihword, hword_asciipart
    WITH unaccent, simple;
