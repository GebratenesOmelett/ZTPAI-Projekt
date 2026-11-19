package pl.domradar.property.event;

/** Nowa nieruchomość pojawiła się w systemie (publikowane w transakcji, obsługiwane po commicie). */
public record PropertyCreatedEvent(Long propertyId, Long importJobId) {
}
