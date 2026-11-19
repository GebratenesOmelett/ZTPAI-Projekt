package pl.domradar.savedsearch;

public enum AlertMode {
    /** Powiadomienie od razu po imporcie, w którym pojawiły się pasujące ogłoszenia. */
    INSTANT,
    /** Dzienny digest wysyłany przez scheduler. */
    DAILY
}
