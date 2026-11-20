-- Porządki w danych zaimportowanych przed regułami PricePlausibility i AreaPlausibility (stosowanymi od teraz
-- przy imporcie). Oferty są dezaktywowane (nie usuwane), a nieruchomości przeliczane jak w PropertyRecalculator.

-- 1) Nierealny metraż mieszkania/pokoju (> 400 m²) lub domu (> 2000 m²), np. 5158 zamiast 51,58:
--    metraż z tytułu którejkolwiek oferty ("2 pokoje / 47 m²"), a bez niego dezaktywacja ofert.
CREATE TEMP TABLE area_fix AS
SELECT p.id AS property_id,
       (SELECT min(replace(substring(o.title FROM '(?<![0-9.,])(?<![0-9][[:space:]])([0-9]{1,4}(?:[.,][0-9]{1,2})?)[[:space:]]?(?:m²|m2|mkw)'), ',', '.')::numeric)
        FROM offer o WHERE o.property_id = p.id) AS title_area,
       CASE WHEN p.property_type = 'HOUSE' THEN 2000 ELSE 400 END AS max_area
FROM property p
WHERE (p.property_type IN ('APARTMENT', 'ROOM') AND p.area > 400)
   OR (p.property_type = 'HOUSE' AND p.area > 2000);

UPDATE property p
SET area = f.title_area
FROM area_fix f
WHERE p.id = f.property_id AND f.title_area BETWEEN 5 AND f.max_area;

CREATE TEMP TABLE implausible_offer AS
SELECT o.id, o.property_id
FROM offer o
         JOIN area_fix f ON f.property_id = o.property_id
WHERE o.active AND (f.title_area IS NULL OR f.title_area NOT BETWEEN 5 AND f.max_area);

-- 2) Cena, która nie jest ceną oferty: symboliczne kwoty < 100 zł oraz najem mieszkań/domów < 500 zł
--    lub < 10 zł/m² (cena za dobę albo za osobę).
INSERT INTO implausible_offer
SELECT o.id, o.property_id
FROM offer o
         JOIN property p ON p.id = o.property_id
WHERE o.active
  AND (o.price < 100
    OR (p.transaction_type = 'RENT' AND p.property_type IN ('APARTMENT', 'HOUSE')
        AND (o.price < 500 OR (p.area > 0 AND o.price / p.area < 10))));

UPDATE offer SET active = FALSE WHERE id IN (SELECT id FROM implausible_offer);

-- 3) Przeliczenie ceny, ceny za m², liczby portali i statusu zmienionych nieruchomości
UPDATE property p
SET current_price       = s.min_price,
    price_per_m2        = CASE WHEN s.min_price IS NULL OR p.area IS NULL OR p.area = 0 THEN NULL
                               ELSE round(s.min_price / p.area, 2) END,
    active_source_count = s.sources,
    status              = CASE WHEN s.sources = 0 THEN 'ARCHIVED' ELSE 'ACTIVE' END
FROM (SELECT o.property_id,
             min(o.price) FILTER (WHERE o.active)                AS min_price,
             count(DISTINCT o.source_id) FILTER (WHERE o.active) AS sources
      FROM offer o
      WHERE o.property_id IN (SELECT property_id FROM implausible_offer UNION SELECT property_id FROM area_fix)
      GROUP BY o.property_id) s
WHERE p.id = s.property_id;

DROP TABLE implausible_offer;
DROP TABLE area_fix;
