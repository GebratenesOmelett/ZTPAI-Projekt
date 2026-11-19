-- Źródło dla ofert dodawanych ręcznie przez administratora (nie jest importowane, więc ręczne oferty
-- nie podlegają regule "3 importów bez wystąpienia").
INSERT INTO source (code, name, base_url, adapter, enabled, import_cron)
VALUES ('manual', 'Dodane ręcznie', 'https://domradar.local', 'manual', FALSE, NULL)
ON CONFLICT (code) DO NOTHING;
