package pl.domradar.ingestion.normalize;

import pl.domradar.ingestion.normalize.DictionaryLookup.CityRef;
import pl.domradar.ingestion.normalize.DictionaryLookup.DistrictRef;

/** Wyznacza współrzędne ogłoszenia, gdy portal ich nie podał. */
public interface Geocoder {

    record GeoPoint(double lat, double lon) {
    }

    GeoPoint geocode(CityRef city, DistrictRef district, String street);
}
