package pl.domradar.common.cache;

/** Dane widoczne w wyszukiwarce zmieniły się poza importem (edycja, scalenie, rozdzielenie przez admina). */
public record SearchDataChangedEvent(String reason) {
}
