package pl.domradar.property.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.common.cache.SearchDataChangedEvent;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.common.web.PageResponse;
import pl.domradar.favorite.Favorite;
import pl.domradar.favorite.FavoriteRepository;
import pl.domradar.property.*;
import pl.domradar.property.admin.dto.*;
import pl.domradar.property.dictionary.FeatureRepository;
import pl.domradar.property.dto.PropertyDetailsDto;

import java.util.*;

/**
 * Edycja, usuwanie, scalanie i rozdzielanie nieruchomości przez administratora.
 * Reguła biznesowa nr 7: każda modyfikacja wymaga przesłania wersji z odczytu; niezgodność → 409 VERSION_CONFLICT.
 * Dodatkowo kolumna {@code @Version} chroni przed wyścigiem między sprawdzeniem a zapisem (UPDATE ... WHERE version = ?).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyAdminService {

    private final PropertyRepository propertyRepository;
    private final OfferRepository offerRepository;
    private final FavoriteRepository favoriteRepository;
    private final FeatureRepository featureRepository;
    private final PropertyRecalculator recalculator;
    private final PropertyQueryService queryService;
    private final ManualListingFactory listingFactory;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public PageResponse<AdminPropertyDto> list(String q, PropertyStatus status, Long cityId, Pageable pageable) {
        return PageResponse.of(propertyRepository.adminSearch(q == null || q.isBlank() ? null : q.trim(), status, cityId, pageable),
                PropertyAdminService::toAdminDto);
    }

    @Transactional(readOnly = true)
    public PropertyDetailsDto get(Long id) {
        return queryService.details(id, null);
    }

    @Transactional
    public PropertyDetailsDto update(Long id, PropertyUpdateRequest request) {
        Property p = find(id);
        checkVersion(p, request.version());
        PropertyAttributesRequest a = request.attributes();
        ManualListingFactory.Location location = listingFactory.resolve(a.cityId(), a.districtId());
        double[] coords = listingFactory.coordinates(a, location);
        // zapytania przed modyfikacjami encji — unikamy auto-flush w połowie zmian (podwójny wzrost wersji)
        List<pl.domradar.property.dictionary.Feature> features = a.features() == null ? null
                : featureRepository.findByCodeIn(a.features().stream().map(f -> f.toUpperCase(Locale.ROOT)).toList());
        List<Offer> offers = offerRepository.findByPropertyId(p.getId());
        p.setPropertyType(a.propertyType());
        p.setTransactionType(a.transactionType());
        p.setMarketType(a.marketType());
        p.setCity(location.city());
        p.setDistrict(location.district());
        p.setStreet(a.street());
        p.setLocation(PropertyFactory.point(coords[0], coords[1]));
        p.setArea(a.area());
        p.setRooms(a.rooms());
        p.setFloor(a.floor());
        p.setTotalFloors(a.totalFloors());
        p.setYearBuilt(a.yearBuilt());
        if (features != null) {
            p.getFeatures().clear();
            p.getFeatures().addAll(features);
        }
        recalculator.recalculate(p, offers);
        propertyRepository.flush();
        events.publishEvent(new SearchDataChangedEvent("property " + id + " updated"));
        log.info("Property {} updated by admin (version {} -> {})", id, request.version(), p.getVersion());
        return queryService.toDetails(p, null);
    }

    @Transactional
    public void delete(Long id) {
        Property p = find(id);
        offerRepository.deleteAll(offerRepository.findByPropertyId(id));
        favoriteRepository.deleteAll(favoriteRepository.findByPropertyId(id));
        propertyRepository.delete(p);
        events.publishEvent(new SearchDataChangedEvent("property " + id + " deleted"));
    }

    /** Scala nieruchomość źródłową z docelową: oferty, ulubione i cechy przechodzą do docelowej. */
    @Transactional
    public PropertyDetailsDto merge(Long targetId, MergeRequest request) {
        if (targetId.equals(request.sourcePropertyId())) {
            throw new DomainException(ErrorCode.MERGE_NOT_ALLOWED, "Nie można scalić nieruchomości z samą sobą");
        }
        Property target = find(targetId);
        Property source = find(request.sourcePropertyId());
        checkVersion(target, request.targetVersion());
        checkVersion(source, request.sourceVersion());
        if (!target.getCity().getId().equals(source.getCity().getId())
                || target.getPropertyType() != source.getPropertyType()
                || target.getTransactionType() != source.getTransactionType()) {
            throw new DomainException(ErrorCode.MERGE_NOT_ALLOWED,
                    "Scalać można tylko nieruchomości z tego samego miasta, tego samego typu i rodzaju transakcji");
        }
        for (Offer offer : offerRepository.findByPropertyId(source.getId())) {
            offer.setProperty(target);
        }
        target.getFeatures().addAll(source.getFeatures());
        if (target.getDistrict() == null) target.setDistrict(source.getDistrict());
        if (target.getStreet() == null) target.setStreet(source.getStreet());
        if (target.getRooms() == null) target.setRooms(source.getRooms());
        if (target.getFloor() == null) target.setFloor(source.getFloor());
        if (target.getTotalFloors() == null) target.setTotalFloors(source.getTotalFloors());
        if (target.getYearBuilt() == null) target.setYearBuilt(source.getYearBuilt());
        if (source.getFirstSeenAt().isBefore(target.getFirstSeenAt())) target.setFirstSeenAt(source.getFirstSeenAt());
        for (Favorite favorite : favoriteRepository.findByPropertyId(source.getId())) {
            if (favoriteRepository.existsByUserIdAndPropertyId(favorite.getUser().getId(), target.getId())) {
                favoriteRepository.delete(favorite);
            } else {
                favorite.setProperty(target);
            }
        }
        offerRepository.flush();
        favoriteRepository.flush();
        propertyRepository.delete(source);
        recalculator.recalculate(target, offerRepository.findByPropertyId(target.getId()));
        propertyRepository.flush();
        events.publishEvent(new SearchDataChangedEvent("property " + source.getId() + " merged into " + targetId));
        log.info("Property {} merged into {}", source.getId(), targetId);
        return queryService.toDetails(target, null);
    }

    /** Wydziela wskazane oferty do nowej nieruchomości utworzonej z atrybutów pierwszej z nich. */
    @Transactional
    public SplitResponse split(Long id, SplitRequest request) {
        Property original = find(id);
        checkVersion(original, request.version());
        List<Offer> offers = offerRepository.findByPropertyId(id);
        Set<Long> requested = new LinkedHashSet<>(request.offerIds());
        List<Offer> moved = offers.stream().filter(o -> requested.contains(o.getId())).toList();
        if (moved.size() != requested.size()) {
            throw new DomainException(ErrorCode.SPLIT_NOT_ALLOWED, "Wszystkie wskazane oferty muszą należeć do tej nieruchomości");
        }
        if (moved.size() == offers.size()) {
            throw new DomainException(ErrorCode.SPLIT_NOT_ALLOWED, "Przy nieruchomości musi pozostać co najmniej jedna oferta");
        }
        Offer first = moved.stream().filter(o -> o.getId().equals(request.offerIds().getFirst())).findFirst().orElse(moved.getFirst());
        Property created = propertyRepository.save(fromOffer(original, first));
        for (Offer offer : moved) {
            offer.setProperty(created);
        }
        offerRepository.flush();
        recalculator.recalculate(original, offerRepository.findByPropertyId(original.getId()));
        recalculator.recalculate(created, offerRepository.findByPropertyId(created.getId()));
        propertyRepository.flush();
        events.publishEvent(new SearchDataChangedEvent("property " + id + " split"));
        log.info("Property {} split: {} offers moved to new property {}", id, moved.size(), created.getId());
        return new SplitResponse(queryService.toDetails(original, null), queryService.toDetails(created, null));
    }

    private Property fromOffer(Property original, Offer offer) {
        OfferAttributes a = offer.getAttributes();
        Property p = new Property();
        p.setPropertyType(a != null && a.propertyType() != null ? a.propertyType() : original.getPropertyType());
        p.setTransactionType(a != null && a.transactionType() != null ? a.transactionType() : original.getTransactionType());
        p.setMarketType(a != null ? a.marketType() : original.getMarketType());
        Long cityId = a != null && a.cityId() != null ? a.cityId() : original.getCity().getId();
        Long districtId = a != null ? a.districtId() : null;
        ManualListingFactory.Location location = listingFactory.resolve(cityId,
                districtId != null ? districtId : original.getDistrict() == null ? null : original.getDistrict().getId());
        p.setCity(location.city());
        p.setDistrict(location.district());
        p.setStreet(a != null ? a.street() : original.getStreet());
        double lat = a != null && a.lat() != null ? a.lat() : original.getLocation().getY();
        double lon = a != null && a.lon() != null ? a.lon() : original.getLocation().getX();
        p.setLocation(PropertyFactory.point(lat, lon));
        p.setArea(a != null && a.area() != null ? a.area() : original.getArea());
        p.setRooms(a != null ? a.rooms() : original.getRooms());
        p.setFloor(a != null ? a.floor() : original.getFloor());
        p.setTotalFloors(a != null ? a.totalFloors() : original.getTotalFloors());
        p.setYearBuilt(a != null ? a.yearBuilt() : original.getYearBuilt());
        if (a != null && a.features() != null) {
            p.getFeatures().addAll(featureRepository.findByCodeIn(a.features()));
        }
        p.setTitle(offer.getTitle());
        p.setFirstSeenAt(offer.getFirstSeenAt());
        p.setLastSeenAt(offer.getLastSeenAt());
        p.setStatus(PropertyStatus.ACTIVE);
        return p;
    }

    /** Reguła nr 7 — optimistic locking na poziomie API. */
    static void checkVersion(Property property, Long expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(property.getVersion())) {
            throw new DomainException(ErrorCode.VERSION_CONFLICT,
                    "Nieruchomość " + property.getId() + " została w międzyczasie zmieniona. Odśwież dane i spróbuj ponownie.",
                    Map.of("propertyId", property.getId(), "currentVersion", property.getVersion(),
                            "sentVersion", String.valueOf(expectedVersion)));
        }
    }

    private Property find(Long id) {
        return propertyRepository.findDetailedById(id).orElseThrow(() -> DomainException.notFound("Nieruchomość", id));
    }

    static AdminPropertyDto toAdminDto(Property p) {
        return new AdminPropertyDto(p.getId(), p.getTitle(), p.getPropertyType(), p.getTransactionType(),
                p.getCity().getName(), p.getDistrict() == null ? null : p.getDistrict().getName(), p.getStreet(),
                p.getArea(), p.getRooms(), p.getFloor(), p.getCurrentPrice(), p.getPricePerM2(), p.getStatus(),
                p.getActiveSourceCount(), p.getFirstSeenAt(), p.getLastSeenAt(), p.getVersion());
    }
}
