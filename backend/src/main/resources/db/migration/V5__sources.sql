-- Źródła danych. Portale "demo" są obsługiwane przez DemoAdapter (dane syntetyczne),
-- "nieruchomosci-online" przez SnapshotAdapter (lokalne pliki JSON/HTML),
-- "otodom-live" to szkielet adaptera HTTP — domyślnie wyłączony (aspekt prawny, patrz README).
INSERT INTO source (code, name, base_url, adapter, enabled, import_cron)
VALUES ('otodom', 'Otodom', 'https://www.otodom.pl', 'demo', TRUE, '0 0 */6 * * *'),
       ('morizon', 'Morizon', 'https://www.morizon.pl', 'demo', TRUE, '0 15 */6 * * *'),
       ('gratka', 'Gratka', 'https://gratka.pl', 'demo', TRUE, '0 30 */6 * * *'),
       ('olx', 'OLX', 'https://www.olx.pl', 'demo', TRUE, '0 45 */6 * * *'),
       ('nieruchomosci-online', 'Nieruchomości-online', 'https://www.nieruchomosci-online.pl', 'snapshot', TRUE, '0 0 3 * * *'),
       ('otodom-live', 'Otodom (HTTP, wyłączony)', 'https://www.otodom.pl', 'otodom-http', FALSE, NULL);
