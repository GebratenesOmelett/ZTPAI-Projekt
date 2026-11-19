-- =====================================================================
-- Słownik miast (współrzędne centrum)
-- =====================================================================
INSERT INTO city (name, slug, lat, lon)
VALUES ('Warszawa', 'warszawa', 52.2297, 21.0122),
       ('Kraków', 'krakow', 50.0647, 19.9450),
       ('Wrocław', 'wroclaw', 51.1079, 17.0385),
       ('Gdańsk', 'gdansk', 54.3520, 18.6466),
       ('Poznań', 'poznan', 52.4064, 16.9252),
       ('Łódź', 'lodz', 51.7592, 19.4560);

-- =====================================================================
-- Słownik dzielnic (przybliżone centroidy + aliasy używane przy normalizacji)
-- aliases: lista rozdzielona znakiem '|', porównywana po f_unaccent(lower(...))
-- =====================================================================
INSERT INTO district (city_id, name, slug, aliases, lat, lon)
SELECT c.id, d.name, d.slug, d.aliases, d.lat, d.lon
FROM (VALUES
          -- Warszawa
          ('warszawa', 'Mokotów', 'mokotow', 'mokotow|gorny mokotow|dolny mokotow|sluzew|sielce|stegny', 52.1934, 21.0346),
          ('warszawa', 'Śródmieście', 'srodmiescie', 'srodmiescie|srodmiescie polnocne|srodmiescie poludniowe|centrum|powisle', 52.2310, 21.0131),
          ('warszawa', 'Wola', 'wola', 'wola|mirow|czyste|odolany', 52.2361, 20.9600),
          ('warszawa', 'Ochota', 'ochota', 'ochota|rakowiec|szczesliwice', 52.2122, 20.9727),
          ('warszawa', 'Żoliborz', 'zoliborz', 'zoliborz|sady zoliborskie', 52.2694, 20.9826),
          ('warszawa', 'Praga-Południe', 'praga-poludnie', 'praga-poludnie|praga poludnie|praga pd|saska kepa|gocław|goclaw|grochow', 52.2394, 21.0800),
          ('warszawa', 'Praga-Północ', 'praga-polnoc', 'praga-polnoc|praga polnoc|praga pn|nowa praga', 52.2577, 21.0347),
          ('warszawa', 'Bielany', 'bielany', 'bielany|mlociny|wawrzyszew', 52.2895, 20.9378),
          ('warszawa', 'Bemowo', 'bemowo', 'bemowo|jelonki|gorce', 52.2545, 20.9110),
          ('warszawa', 'Ursynów', 'ursynow', 'ursynow|kabaty|natolin|imielin', 52.1494, 21.0450),
          ('warszawa', 'Wilanów', 'wilanow', 'wilanow|miasteczko wilanow', 52.1630, 21.0880),
          ('warszawa', 'Targówek', 'targowek', 'targowek|brodno|zacisze', 52.2905, 21.0573),
          ('warszawa', 'Białołęka', 'bialoleka', 'bialoleka|tarchomin|nowodwory', 52.3192, 20.9966),
          ('warszawa', 'Ursus', 'ursus', 'ursus|niedzwiadek', 52.1955, 20.8846),
          ('warszawa', 'Włochy', 'wlochy', 'wlochy|okecie', 52.1946, 20.9300),
          ('warszawa', 'Wawer', 'wawer', 'wawer|anin|marysin wawerski', 52.2000, 21.1700),
          -- Kraków
          ('krakow', 'Stare Miasto', 'stare-miasto', 'stare miasto|kazimierz|kleparz|centrum', 50.0614, 19.9372),
          ('krakow', 'Grzegórzki', 'grzegorzki', 'grzegorzki|dabie', 50.0590, 19.9640),
          ('krakow', 'Prądnik Czerwony', 'pradnik-czerwony', 'pradnik czerwony|olsza|ugorek', 50.0900, 19.9700),
          ('krakow', 'Prądnik Biały', 'pradnik-bialy', 'pradnik bialy|azory|bronowice wielkie', 50.0950, 19.9250),
          ('krakow', 'Krowodrza', 'krowodrza', 'krowodrza|lobzow|nowa wies', 50.0750, 19.9100),
          ('krakow', 'Bronowice', 'bronowice', 'bronowice|bronowice male', 50.0800, 19.8800),
          ('krakow', 'Zwierzyniec', 'zwierzyniec', 'zwierzyniec|salwator|wola justowska', 50.0550, 19.8800),
          ('krakow', 'Dębniki', 'debniki', 'debniki|ruczaj|kobierzyn', 50.0350, 19.9150),
          ('krakow', 'Podgórze', 'podgorze', 'podgorze|zablocie|plaszow', 50.0400, 19.9600),
          ('krakow', 'Czyżyny', 'czyzyny', 'czyzyny', 50.0700, 20.0100),
          ('krakow', 'Nowa Huta', 'nowa-huta', 'nowa huta|bienczyce|mistrzejowice', 50.0700, 20.0400),
          ('krakow', 'Bieżanów-Prokocim', 'biezanow-prokocim', 'biezanow-prokocim|biezanow|prokocim', 50.0200, 20.0100),
          -- Wrocław
          ('wroclaw', 'Stare Miasto', 'stare-miasto', 'stare miasto|rynek|centrum', 51.1100, 17.0300),
          ('wroclaw', 'Śródmieście', 'srodmiescie', 'srodmiescie|nadodrze|olbin|plac grunwaldzki', 51.1180, 17.0550),
          ('wroclaw', 'Krzyki', 'krzyki', 'krzyki|borek|gaj|jagodno|klecina', 51.0750, 17.0100),
          ('wroclaw', 'Fabryczna', 'fabryczna', 'fabryczna|lesnica|nowy dwor|muchobor', 51.1200, 16.9500),
          ('wroclaw', 'Psie Pole', 'psie-pole', 'psie pole|karlowice|sołtysowice|soltysowice', 51.1500, 17.0800),
          -- Gdańsk
          ('gdansk', 'Śródmieście', 'srodmiescie', 'srodmiescie|glowne miasto|stare miasto', 54.3500, 18.6500),
          ('gdansk', 'Wrzeszcz', 'wrzeszcz', 'wrzeszcz|wrzeszcz gorny|wrzeszcz dolny', 54.3800, 18.6000),
          ('gdansk', 'Oliwa', 'oliwa', 'oliwa', 54.4100, 18.5600),
          ('gdansk', 'Przymorze', 'przymorze', 'przymorze|przymorze wielkie|przymorze male', 54.4100, 18.5900),
          ('gdansk', 'Zaspa', 'zaspa', 'zaspa|zaspa-mlyniec|zaspa-rozstaje', 54.3950, 18.6050),
          ('gdansk', 'Jasień', 'jasien', 'jasien', 54.3500, 18.5600),
          ('gdansk', 'Ujeścisko-Łostowice', 'ujescisko-lostowice', 'ujescisko-lostowice|ujescisko|lostowice', 54.3300, 18.5900),
          ('gdansk', 'Letnica', 'letnica', 'letnica', 54.3800, 18.6400),
          ('gdansk', 'Brzeźno', 'brzezno', 'brzezno', 54.4100, 18.6300),
          -- Poznań
          ('poznan', 'Stare Miasto', 'stare-miasto', 'stare miasto|centrum', 52.4100, 16.9330),
          ('poznan', 'Jeżyce', 'jezyce', 'jezyce|ogrody|soleckie', 52.4150, 16.8950),
          ('poznan', 'Grunwald', 'grunwald', 'grunwald|gorczyn|junikowo', 52.3950, 16.8800),
          ('poznan', 'Wilda', 'wilda', 'wilda|debiec', 52.3900, 16.9200),
          ('poznan', 'Łazarz', 'lazarz', 'lazarz|sw. lazarz|swiety lazarz', 52.3950, 16.9000),
          ('poznan', 'Rataje', 'rataje', 'rataje|chartowo', 52.3900, 16.9650),
          ('poznan', 'Winogrady', 'winogrady', 'winogrady|wilczy mlyn', 52.4300, 16.9350),
          ('poznan', 'Piątkowo', 'piatkowo', 'piatkowo|morasko', 52.4600, 16.9150),
          -- Łódź
          ('lodz', 'Śródmieście', 'srodmiescie', 'srodmiescie|centrum|piotrkowska', 51.7700, 19.4600),
          ('lodz', 'Bałuty', 'baluty', 'baluty|teofilow|radogoszcz', 51.8000, 19.4300),
          ('lodz', 'Górna', 'gorna', 'gorna|chojny|dabrowa', 51.7300, 19.4700),
          ('lodz', 'Polesie', 'polesie', 'polesie|retkinia|koziny', 51.7600, 19.4100),
          ('lodz', 'Widzew', 'widzew', 'widzew|olechow|stoki', 51.7600, 19.5200)
     ) AS d(city_slug, name, slug, aliases, lat, lon)
         JOIN city c ON c.slug = d.city_slug;

-- =====================================================================
-- Słownik cech nieruchomości
-- =====================================================================
INSERT INTO feature (code, name)
VALUES ('BALCONY', 'Balkon'),
       ('TERRACE', 'Taras'),
       ('GARDEN', 'Ogródek'),
       ('GARAGE', 'Garaż / miejsce parkingowe'),
       ('ELEVATOR', 'Winda'),
       ('BASEMENT', 'Piwnica'),
       ('STORAGE', 'Komórka lokatorska'),
       ('AIR_CONDITIONING', 'Klimatyzacja'),
       ('FURNISHED', 'Umeblowane'),
       ('MONITORING', 'Monitoring / ochrona'),
       ('LOGGIA', 'Loggia'),
       ('SEPARATE_KITCHEN', 'Oddzielna kuchnia');
