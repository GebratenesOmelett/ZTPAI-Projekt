package pl.domradar.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.ingestion.dedup.DeduplicationService;
import pl.domradar.ingestion.normalize.NormalizedListing;
import pl.domradar.property.*;
import pl.domradar.property.dictionary.CityRepository;
import pl.domradar.property.dictionary.DistrictRepository;
import pl.domradar.property.dictionary.FeatureRepository;
import pl.domradar.property.event.PriceDroppedEvent;
import pl.domradar.property.event.PropertyCreatedEvent;
import pl.domradar.property.lifecycle.OfferLifecyclePolicy;
import pl.domradar.source.SourceRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Zapis znormalizowanego ogłoszenia: aktualizacja istniejącej oferty (historia cen tylko przy realnej zmianie
 * ceny — reguła nr 2) albo nowa oferta dołączona do istniejącej nieruchomości (deduplikacja — reguła nr 1)
 * lub do nowej nieruchomości. Każde ogłoszenie w osobnej transakcji, eventy publikowane po commicie.
 */
@Service
@RequiredArgsConstructor
public class ListingUpsertService {

    public enum Outcome {
        CREATED, MERGED, UPDATED, UNCHANGED
    }

    private final OfferRepository offerRepository;
    private final PropertyRepository propertyRepository;
    private final SourceRepository sourceRepository;
    private final CityRepository cityRepository;
    private final DistrictRepository districtRepository;
    private final FeatureRepository featureRepository;
    private final DeduplicationService deduplicationService;
    private final PropertyRecalculator recalculator;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome upsert(Long jobId, Long sourceId, NormalizedListing listing) {
        return doUpsert(jobId, sourceId, listing, null, true);
    }

    /**
     * Ręczne dodanie oferty przez administratora (w bieżącej transakcji): do wskazanej nieruchomości,
     * przez deduplikację ({@code allowMerge}) albo zawsze jako nowa nieruchomość.
     */
    @Transactional
    public Outcome addManual(Long sourceId, NormalizedListing listing, Long targetPropertyId, boolean allowMerge) {
        return doUpsert(null, sourceId, listing, targetPropertyId, allowMerge);
    }

    private Outcome doUpsert(Long jobId, Long sourceId, NormalizedListing listing, Long targetPropertyId, boolean allowMerge) {
        Instant now = clock.instant();
        Optional<Offer> existing = offerRepository.findBySourceIdAndExternalId(sourceId, listing.externalId());
        Property property;
        Outcome outcome;
        if (existing.isPresent()) {
            Offer offer = existing.get();
            boolean reactivated = !offer.isActive();
            boolean changed = applyUpdate(offer, listing, now);
            OfferLifecyclePolicy.markSeen(offer, jobId, now);
            property = offer.getProperty();
            if (!changed && !reactivated) {
                property.setLastSeenAt(now);
                return Outcome.UNCHANGED;
            }
            outcome = Outcome.UPDATED;
        } else {
            Optional<Long> matchedId = targetPropertyId != null ? Optional.of(targetPropertyId)
                    : allowMerge ? deduplicationService.findMatch(listing, sourceId).map(DeduplicationService.Match::propertyId)
                    : Optional.empty();
            if (matchedId.isPresent()) {
                property = propertyRepository.findById(matchedId.get()).orElseThrow();
                enrich(property, listing);
                outcome = Outcome.MERGED;
            } else {
                property = propertyRepository.save(newProperty(listing, now));
                outcome = Outcome.CREATED;
            }
            offerRepository.save(newOffer(property, sourceId, jobId, listing, now));
        }
        if (!listing.featureCodes().isEmpty()) {
            property.getFeatures().addAll(featureRepository.findByCodeIn(listing.featureCodes()));
        }
        PropertyRecalculator.Result result = recalculator.recalculate(property, offerRepository.findByPropertyId(property.getId()));
        if (outcome == Outcome.CREATED) {
            events.publishEvent(new PropertyCreatedEvent(property.getId(), jobId));
        } else if (result.priceDecreased()) {
            events.publishEvent(new PriceDroppedEvent(property.getId(), jobId, result.priceBefore(), result.priceAfter()));
        }
        return outcome;
    }

    /** @return true, jeśli zmieniła się cena lub istotne dane oferty */
    private boolean applyUpdate(Offer offer, NormalizedListing n, Instant now) {
        boolean changed = false;
        if (offer.getPrice().compareTo(n.price()) != 0) {
            offer.addPriceHistory(n.price(), now);
            offer.setPrice(n.price());
            changed = true;
        }
        if (!Objects.equals(offer.getTitle(), n.title()) || !Objects.equals(offer.getDescription(), n.description())) {
            offer.setTitle(n.title());
            offer.setDescription(n.description());
            changed = true;
        }
        OfferAttributes attributes = attributes(n);
        if (!Objects.equals(offer.getAttributes(), attributes)) {
            offer.setAttributes(attributes);
            changed = true;
        }
        offer.setUrl(n.url());
        offer.setAgencyName(n.agencyName());
        offer.setPrivateSeller(n.privateSeller());
        List<String> currentPhotos = offer.getPhotos().stream().map(Photo::getUrl).toList();
        if (!currentPhotos.equals(n.photos())) {
            offer.replacePhotos(n.photos());
        }
        return changed;
    }

    private Property newProperty(NormalizedListing n, Instant now) {
        Property p = new Property();
        p.setFingerprint(PropertyFactory.fingerprint(n.cityId(), n.districtId(), n.propertyType(), n.transactionType(),
                n.area(), n.rooms(), n.floor(), n.street()));
        p.setPropertyType(n.propertyType());
        p.setTransactionType(n.transactionType());
        p.setMarketType(n.marketType());
        p.setCity(cityRepository.getReferenceById(n.cityId()));
        p.setDistrict(n.districtId() == null ? null : districtRepository.getReferenceById(n.districtId()));
        p.setStreet(n.street());
        p.setLocation(PropertyFactory.point(n.lat(), n.lon()));
        p.setArea(n.area());
        p.setRooms(n.rooms());
        p.setFloor(n.floor());
        p.setTotalFloors(n.totalFloors());
        p.setYearBuilt(n.yearBuilt());
        p.setTitle(n.title());
        p.setFirstSeenAt(now);
        p.setLastSeenAt(now);
        p.setStatus(PropertyStatus.ACTIVE);
        return p;
    }

    /** Uzupełnia brakujące atrybuty nieruchomości danymi z nowego portalu (bez nadpisywania istniejących). */
    private void enrich(Property p, NormalizedListing n) {
        if (p.getDistrict() == null && n.districtId() != null) {
            p.setDistrict(districtRepository.getReferenceById(n.districtId()));
        }
        if (p.getStreet() == null) p.setStreet(n.street());
        if (p.getRooms() == null) p.setRooms(n.rooms());
        if (p.getFloor() == null) p.setFloor(n.floor());
        if (p.getTotalFloors() == null) p.setTotalFloors(n.totalFloors());
        if (p.getYearBuilt() == null) p.setYearBuilt(n.yearBuilt());
        if (p.getMarketType() == null) p.setMarketType(n.marketType());
    }

    private Offer newOffer(Property property, Long sourceId, Long jobId, NormalizedListing n, Instant now) {
        Offer offer = new Offer();
        offer.setProperty(property);
        offer.setSource(sourceRepository.getReferenceById(sourceId));
        offer.setExternalId(n.externalId());
        offer.setUrl(n.url());
        offer.setTitle(n.title());
        offer.setDescription(n.description());
        offer.setPrice(n.price());
        offer.setAgencyName(n.agencyName());
        offer.setPrivateSeller(n.privateSeller());
        offer.setAttributes(attributes(n));
        offer.setFirstSeenAt(now);
        OfferLifecyclePolicy.markSeen(offer, jobId, now);
        offer.addPriceHistory(n.price(), now);
        offer.replacePhotos(n.photos());
        return offer;
    }

    public static OfferAttributes attributes(NormalizedListing n) {
        return new OfferAttributes(n.propertyType(), n.transactionType(), n.marketType(), n.cityId(), n.districtId(),
                n.street(), n.lat(), n.lon(), n.area(), n.rooms(), n.floor(), n.totalFloors(), n.yearBuilt(),
                new ArrayList<>(n.featureCodes()));
    }
}
