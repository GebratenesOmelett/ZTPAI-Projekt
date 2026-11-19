package pl.domradar.ingestion.adapter.demo;

import java.util.List;
import java.util.Map;

/**
 * Orientacyjne ceny transakcyjne za m² (sprzedaż, rynek wtórny, 2026) per dzielnica oraz listy ulic.
 * Dane są przybliżone i służą wyłącznie do generowania realistycznych danych demonstracyjnych.
 */
final class DemoPriceTable {

    private DemoPriceTable() {
    }

    static final Map<String, Integer> PRICE_PER_M2 = Map.ofEntries(
            // Warszawa
            Map.entry("warszawa/srodmiescie", 26000), Map.entry("warszawa/mokotow", 19500),
            Map.entry("warszawa/wola", 19000), Map.entry("warszawa/ochota", 18000),
            Map.entry("warszawa/zoliborz", 20500), Map.entry("warszawa/praga-poludnie", 16000),
            Map.entry("warszawa/praga-polnoc", 16500), Map.entry("warszawa/bielany", 15500),
            Map.entry("warszawa/bemowo", 14500), Map.entry("warszawa/ursynow", 16000),
            Map.entry("warszawa/wilanow", 17500), Map.entry("warszawa/targowek", 13500),
            Map.entry("warszawa/bialoleka", 11500), Map.entry("warszawa/ursus", 12500),
            Map.entry("warszawa/wlochy", 14000), Map.entry("warszawa/wawer", 11500),
            // Kraków
            Map.entry("krakow/stare-miasto", 22000), Map.entry("krakow/grzegorzki", 18000),
            Map.entry("krakow/pradnik-czerwony", 14500), Map.entry("krakow/pradnik-bialy", 14500),
            Map.entry("krakow/krowodrza", 16500), Map.entry("krakow/bronowice", 15000),
            Map.entry("krakow/zwierzyniec", 18000), Map.entry("krakow/debniki", 15500),
            Map.entry("krakow/podgorze", 16000), Map.entry("krakow/czyzyny", 14000),
            Map.entry("krakow/nowa-huta", 11500), Map.entry("krakow/biezanow-prokocim", 12000),
            // Wrocław
            Map.entry("wroclaw/stare-miasto", 17000), Map.entry("wroclaw/srodmiescie", 13500),
            Map.entry("wroclaw/krzyki", 12500), Map.entry("wroclaw/fabryczna", 11500),
            Map.entry("wroclaw/psie-pole", 11000),
            // Gdańsk
            Map.entry("gdansk/srodmiescie", 19000), Map.entry("gdansk/wrzeszcz", 15000),
            Map.entry("gdansk/oliwa", 15500), Map.entry("gdansk/przymorze", 14500),
            Map.entry("gdansk/zaspa", 13500), Map.entry("gdansk/jasien", 11000),
            Map.entry("gdansk/ujescisko-lostowice", 10500), Map.entry("gdansk/letnica", 14000),
            Map.entry("gdansk/brzezno", 15000),
            // Poznań
            Map.entry("poznan/stare-miasto", 12500), Map.entry("poznan/jezyce", 11500),
            Map.entry("poznan/grunwald", 10500), Map.entry("poznan/wilda", 10500),
            Map.entry("poznan/lazarz", 10500), Map.entry("poznan/rataje", 10000),
            Map.entry("poznan/winogrady", 10000), Map.entry("poznan/piatkowo", 9500),
            // Łódź
            Map.entry("lodz/srodmiescie", 8500), Map.entry("lodz/baluty", 7500),
            Map.entry("lodz/gorna", 7200), Map.entry("lodz/polesie", 7500),
            Map.entry("lodz/widzew", 7800)
    );

    static final Map<String, Double> CITY_WEIGHTS = Map.of(
            "warszawa", 0.35, "krakow", 0.20, "wroclaw", 0.15, "gdansk", 0.10, "poznan", 0.10, "lodz", 0.10);

    static final Map<String, List<String>> STREETS = Map.of(
            "warszawa", List.of("Puławska", "Marszałkowska", "Grochowska", "Górczewska", "Kasprzaka", "Grójecka",
                    "Mickiewicza", "Słowackiego", "Belwederska", "Wilanowska", "Rosoła", "Kondratowicza",
                    "Modlińska", "Połczyńska", "Waszyngtona", "Targowa", "Ostrobramska", "Woronicza",
                    "Komisji Edukacji Narodowej", "Sobieskiego"),
            "krakow", List.of("Długa", "Karmelicka", "Kalwaryjska", "Wielicka", "Opolska", "Bronowicka",
                    "Królewska", "Zakopiańska", "Mogilska", "Lea", "Grzegórzecka", "Kamieńskiego"),
            "wroclaw", List.of("Legnicka", "Grabiszyńska", "Powstańców Śląskich", "Jedności Narodowej", "Kromera",
                    "Borowska", "Ślężna", "Krakowska", "Pomorska"),
            "gdansk", List.of("Grunwaldzka", "Kartuska", "Chłopska", "Piastowska", "Jaśkowa Dolina", "Rakoczego",
                    "Łostowicka", "Hallera", "Kołobrzeska"),
            "poznan", List.of("Głogowska", "Grunwaldzka", "Dąbrowskiego", "Garbary", "Winogrady", "Hetmańska",
                    "Bukowska", "Polna", "Kasprowicza"),
            "lodz", List.of("Piotrkowska", "Zgierska", "Kilińskiego", "Pabianicka", "Retkińska", "Rokicińska",
                    "Żeromskiego", "Narutowicza"));

    /** Fikcyjne nazwy biur nieruchomości (celowo nie odpowiadają realnym firmom). */
    static final List<String> AGENCIES = List.of(
            "Agencja Nieruchomości Kamienica", "Nieruchomości Pod Lipami", "Klucz do Domu", "Adres Nieruchomości",
            "Nowy Kąt Pośrednictwo", "Atrium Pośrednictwo", "Kwadrat Nieruchomości", "Dobre Metry",
            "Mieszkaniowo Sp. z o.o.", "Biuro Domowe Ognisko");

    static final List<String> LOCATION_SENTENCES = List.of(
            "W pobliżu stacja metra i przystanki tramwajowe.",
            "Cicha i zielona okolica, kilka minut spacerem do parku.",
            "Bardzo dobra komunikacja z centrum miasta.",
            "W okolicy szkoły, przedszkola, sklepy i punkty usługowe.",
            "Z okien salonu widok na park.",
            "Blisko ścieżki rowerowe i tereny rekreacyjne.",
            "Świetna lokalizacja przy głównej arterii, szybki dojazd tramwajem.");

    static final List<String> CONDITION_SENTENCES = List.of(
            "Mieszkanie po generalnym remoncie.",
            "Do odświeżenia — idealne do zaaranżowania pod siebie.",
            "Stan deweloperski.",
            "Wymaga remontu.",
            "Wykończone w wysokim standardzie, gotowe do zamieszkania.");
}
