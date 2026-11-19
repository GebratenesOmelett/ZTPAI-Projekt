-- Prawdziwe źródła ogłoszeń (adaptery live). Rekordy są włączone, ale faktyczne łączenie z portalami
-- wymaga LIVE_SOURCES_ENABLED=true (patrz README, "Aspekt prawny"). Importy co 6 godzin, tylko najnowsze strony wyników.
UPDATE source
SET name = 'Otodom', enabled = TRUE, import_cron = '0 0 1/6 * * *'
WHERE code = 'otodom-live';

INSERT INTO source (code, name, base_url, adapter, enabled, import_cron)
VALUES ('olx-live', 'OLX', 'https://www.olx.pl', 'olx-api', TRUE, '0 30 1/6 * * *')
ON CONFLICT (code) DO NOTHING;

-- Źródła syntetyczne wyraźnie oznaczone jako demonstracyjne
UPDATE source SET name = name || ' (demo)' WHERE adapter IN ('demo', 'snapshot') AND name NOT LIKE '% (demo)';
