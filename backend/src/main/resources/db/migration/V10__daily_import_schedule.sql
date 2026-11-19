-- Importy raz dziennie (w nocy) zamiast co 6 godzin. Dane są przechowywane w bazie; wyszukiwarka nigdy
-- nie łączy się z portalami przy wejściu na stronę. Jeśli aplikacja była wyłączona o zaplanowanej godzinie,
-- scheduler nadrobi pominięty import po starcie.
UPDATE source SET import_cron = '0 0 4 * * *'  WHERE code = 'otodom-live';
UPDATE source SET import_cron = '0 30 4 * * *' WHERE code = 'olx-live';

-- Źródła demonstracyjne (aktywne tylko przy DEMO_DATA_ENABLED=true) również raz dziennie
UPDATE source SET import_cron = '0 0 3 * * *'  WHERE code = 'otodom';
UPDATE source SET import_cron = '0 10 3 * * *' WHERE code = 'morizon';
UPDATE source SET import_cron = '0 20 3 * * *' WHERE code = 'gratka';
UPDATE source SET import_cron = '0 30 3 * * *' WHERE code = 'olx';
UPDATE source SET import_cron = '0 40 3 * * *' WHERE code = 'nieruchomosci-online';
