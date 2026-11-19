-- Kolejne prawdziwe portale (adaptery live). Jak w V8: rekordy są włączone, ale łączenie z portalami
-- wymaga LIVE_SOURCES_ENABLED=true. Importy raz dziennie, rozłożone w nocy co 20 minut po Otodom i OLX.
INSERT INTO source (code, name, base_url, adapter, enabled, import_cron) VALUES
    ('morizon-live',              'Morizon',              'https://www.morizon.pl',               'morizon-http',              TRUE, '0 0 5 * * *'),
    ('gratka-live',               'Gratka',               'https://gratka.pl',                    'gratka-http',               TRUE, '0 20 5 * * *'),
    ('nieruchomosci-online-live', 'Nieruchomości-online', 'https://www.nieruchomosci-online.pl',  'nieruchomosci-online-http', TRUE, '0 40 5 * * *'),
    ('domiporta-live',            'Domiporta',            'https://www.domiporta.pl',             'domiporta-http',            TRUE, '0 0 6 * * *'),
    ('gethome-live',              'GetHome',              'https://gethome.pl',                   'gethome-http',              TRUE, '0 20 6 * * *'),
    ('szybko-live',               'Szybko.pl',            'https://szybko.pl',                    'szybko-http',               TRUE, '0 40 6 * * *'),
    ('adresowo-live',             'Adresowo',             'https://adresowo.pl',                  'adresowo-http',             TRUE, '0 0 7 * * *'),
    ('sprzedajemy-live',          'Sprzedajemy.pl',       'https://sprzedajemy.pl',               'sprzedajemy-http',          TRUE, '0 20 7 * * *'),
    ('trojmiasto-live',           'Trojmiasto.pl',        'https://ogloszenia.trojmiasto.pl',     'trojmiasto-http',           TRUE, '0 40 7 * * *')
ON CONFLICT (code) DO NOTHING;
