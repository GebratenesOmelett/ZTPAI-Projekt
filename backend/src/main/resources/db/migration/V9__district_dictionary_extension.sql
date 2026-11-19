-- Uzupełnienie słownika dzielnic o nazwy używane przez portale (Otodom, OLX), które nie pasowały do słownika.
INSERT INTO district (city_id, name, slug, aliases, lat, lon)
SELECT c.id, d.name, d.slug, d.aliases, d.lat, d.lon
FROM (VALUES
          ('gdansk', 'Aniołki', 'aniolki', 'aniolki', 54.3640, 18.6300),
          ('gdansk', 'Chełm', 'chelm', 'chelm|chelm z dzielnica gdansk poludnie|gdansk poludnie', 54.3330, 18.6200),
          ('gdansk', 'Orunia Górna-Gdańsk Południe', 'orunia-gorna', 'orunia gorna|orunia gorna gdansk poludnie|orunia gorna - gdansk poludnie', 54.3240, 18.6040),
          ('gdansk', 'Orunia-Św. Wojciech-Lipce', 'orunia', 'orunia|orunia sw wojciech lipce|lipce|sw wojciech', 54.3200, 18.6400),
          ('gdansk', 'Kokoszki', 'kokoszki', 'kokoszki', 54.3580, 18.4970),
          ('gdansk', 'Matarnia', 'matarnia', 'matarnia', 54.3750, 18.5170),
          ('gdansk', 'Piecki-Migowo', 'piecki-migowo', 'piecki migowo|morena', 54.3620, 18.5820),
          ('gdansk', 'Siedlce', 'siedlce', 'siedlce', 54.3480, 18.6200),
          ('gdansk', 'Stogi', 'stogi', 'stogi|stogi z przerobka|przerobka', 54.3670, 18.7150),
          ('gdansk', 'Suchanino', 'suchanino', 'suchanino', 54.3640, 18.6040),
          ('gdansk', 'Żabianka-Wejhera-Jelitkowo-Tysiąclecia', 'zabianka', 'zabianka|zabianka wejhera jelitkowo tysiaclecia|jelitkowo', 54.4200, 18.5900),
          ('gdansk', 'Osowa', 'osowa', 'osowa', 54.4200, 18.4850),
          ('gdansk', 'Nowy Port', 'nowy-port', 'nowy port', 54.4020, 18.6630),
          ('poznan', 'Nowe Miasto', 'nowe-miasto', 'nowe miasto|starolęka|staroleka|staroleka mala|staroleka wielka|lacina|zawady|szczepankowo|warszawskie|srodka', 52.3950, 16.9900),
          ('poznan', 'Naramowice', 'naramowice', 'naramowice', 52.4500, 16.9500)
     ) AS d(city_slug, name, slug, aliases, lat, lon)
         JOIN city c ON c.slug = d.city_slug
ON CONFLICT (city_id, slug) DO NOTHING;

-- Dodatkowe aliasy (osiedla zgłaszane przez portale) dla istniejących dzielnic
UPDATE district SET aliases = aliases || '|lawica|kasztelanow|junikowo|gorczyn'
WHERE slug = 'grunwald' AND city_id = (SELECT id FROM city WHERE slug = 'poznan');
UPDATE district SET aliases = aliases || '|podolany|strzeszyn|solacz|winiary|os lotnikow wielkopolskich|lotnikow wielkopolskich'
WHERE slug = 'jezyce' AND city_id = (SELECT id FROM city WHERE slug = 'poznan');
UPDATE district SET aliases = aliases || '|umultowo'
WHERE slug = 'piatkowo' AND city_id = (SELECT id FROM city WHERE slug = 'poznan');
UPDATE district SET aliases = aliases || '|zaspa mlyniec|zaspa rozstaje'
WHERE slug = 'zaspa' AND city_id = (SELECT id FROM city WHERE slug = 'gdansk');
