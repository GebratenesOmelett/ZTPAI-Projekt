package pl.domradar.ingestion;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import pl.domradar.ingestion.dedup.DeduplicationService;
import pl.domradar.ingestion.normalize.NormalizedListing;
import pl.domradar.property.*;
import pl.domradar.property.dictionary.City;
import pl.domradar.property.dictionary.CityRepository;
import pl.domradar.property.dictionary.DistrictRepository;
import pl.domradar.property.dictionary.FeatureRepository;
import pl.domradar.source.SourceRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Oferta, której rodzaj transakcji zmienił się przy ponownym imporcie (np. "sprzedaż" za 2900 zł → najem). */
class ListingUpsertTransactionChangeTest {

    private final OfferRepository offers = mock(OfferRepository.class);
    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final CityRepository cities = mock(CityRepository.class);
    private final PropertyRecalculator recalculator = mock(PropertyRecalculator.class);
    private final ListingUpsertService service = new ListingUpsertService(offers, properties, mock(SourceRepository.class),
            cities, mock(DistrictRepository.class), mock(FeatureRepository.class), mock(DeduplicationService.class),
            recalculator, mock(ApplicationEventPublisher.class), Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC));

    private Property saleProperty;
    private Offer offer;

    @BeforeEach
    void setUp() {
        saleProperty = new Property();
        saleProperty.setId(1L);
        saleProperty.setTransactionType(TransactionType.SALE);
        saleProperty.setPropertyType(PropertyType.APARTMENT);
        offer = new Offer();
        offer.setId(10L);
        offer.setProperty(saleProperty);
        offer.setExternalId("ext-1");
        offer.setTitle("Mieszkanie");
        offer.setPrice(new BigDecimal("290000.00"));
        offer.setActive(true);
        when(offers.findBySourceIdAndExternalId(5L, "ext-1")).thenReturn(Optional.of(offer));
        when(cities.getReferenceById(any())).thenReturn(new City());
        when(properties.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));
        when(recalculator.recalculate(any(), anyList())).thenReturn(new PropertyRecalculator.Result(null, null, null, null));
    }

    @Test
    void propertyWithSingleOfferChangesTransaction() {
        when(offers.findByPropertyId(1L)).thenReturn(List.of(offer));

        service.upsert(1L, 5L, rentListing());

        assertThat(offer.getProperty()).isSameAs(saleProperty);
        assertThat(saleProperty.getTransactionType()).isEqualTo(TransactionType.RENT);
        verify(properties, never()).save(any());
    }

    @Test
    void offerMergedWithOtherPortalsMovesToNewProperty() {
        Offer sibling = new Offer();
        sibling.setId(11L);
        sibling.setProperty(saleProperty);
        when(offers.findByPropertyId(1L)).thenReturn(List.of(offer, sibling));

        service.upsert(1L, 5L, rentListing());

        assertThat(offer.getProperty()).isNotSameAs(saleProperty);
        assertThat(offer.getProperty().getTransactionType()).isEqualTo(TransactionType.RENT);
        assertThat(saleProperty.getTransactionType()).as("pozostałe oferty bez zmian").isEqualTo(TransactionType.SALE);
        verify(recalculator).recalculate(eq(saleProperty), eq(List.of(sibling)));
    }

    private static NormalizedListing rentListing() {
        return NormalizedListing.builder()
                .externalId("ext-1").title("Mieszkanie").price(new BigDecimal("2900.00"))
                .propertyType(PropertyType.APARTMENT).transactionType(TransactionType.RENT)
                .cityId(1L).cityName("Warszawa").lat(52.23).lon(21.01).area(new BigDecimal("41.20")).rooms(2)
                .featureCodes(Set.of()).photos(List.of()).privateSeller(true)
                .build();
    }
}
