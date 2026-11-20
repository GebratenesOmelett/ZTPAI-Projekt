package pl.domradar.ingestion.adapter.http;

import java.util.List;

/** Miasta obsługiwane przez adaptery portali: slug ze słownika, nazwa i slug województwa (część adresów URL). */
public record PortalCity(String slug, String name, String voivodeship) {

    public static final List<PortalCity> ALL = List.of(
            new PortalCity("warszawa", "Warszawa", "mazowieckie"),
            new PortalCity("krakow", "Kraków", "malopolskie"),
            new PortalCity("wroclaw", "Wrocław", "dolnoslaskie"),
            new PortalCity("gdansk", "Gdańsk", "pomorskie"),
            new PortalCity("poznan", "Poznań", "wielkopolskie"),
            new PortalCity("lodz", "Łódź", "lodzkie"),
            new PortalCity("szczecin", "Szczecin", "zachodniopomorskie"),
            new PortalCity("bydgoszcz", "Bydgoszcz", "kujawsko-pomorskie"),
            new PortalCity("lublin", "Lublin", "lubelskie"),
            new PortalCity("bialystok", "Białystok", "podlaskie"),
            new PortalCity("katowice", "Katowice", "slaskie"),
            new PortalCity("gdynia", "Gdynia", "pomorskie"),
            new PortalCity("rzeszow", "Rzeszów", "podkarpackie"),
            new PortalCity("torun", "Toruń", "kujawsko-pomorskie"),
            new PortalCity("kielce", "Kielce", "swietokrzyskie"),
            new PortalCity("olsztyn", "Olsztyn", "warminsko-mazurskie"),
            new PortalCity("opole", "Opole", "opolskie"));
}
