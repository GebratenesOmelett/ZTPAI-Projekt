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
            new PortalCity("lodz", "Łódź", "lodzkie"));
}
