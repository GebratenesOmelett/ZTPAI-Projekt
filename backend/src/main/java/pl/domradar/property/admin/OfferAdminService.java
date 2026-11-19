package pl.domradar.property.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.common.cache.SearchDataChangedEvent;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.common.web.PageResponse;
import pl.domradar.ingestion.ListingUpsertService;
import pl.domradar.ingestion.normalize.NormalizedListing;
import pl.domradar.property.*;
import pl.domradar.property.admin.dto.OfferCreateRequest;
import pl.domradar.property.admin.dto.OfferUpdateRequest;
import pl.domradar.property.admin.dto.PropertyAttributesRequest;
import pl.domradar.property.dto.OfferDto;
import pl.domradar.property.event.PriceDroppedEvent;
import pl.domradar.source.Source;
import pl.domradar.source.SourceRepository;

import java.time.Instant;
import java.util.Map;

/** CRUD ofert w panelu admina, w tym ręczne dodanie oferty (z deduplikacją lub do wskazanej nieruchomości). */
@Service
@RequiredArgsConstructor
public class OfferAdminService {

    public static final String MANUAL_SOURCE = "manual";

    private final OfferRepository offerRepository;
    private final PropertyRepository propertyRepository;
    private final SourceRepository sourceRepository;
    private final ListingUpsertService upsertService;
    private final ManualListingFactory listingFactory;
    private final PropertyRecalculator recalculator;
    private final OfferMapper mapper;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public PageResponse<OfferDto> list(Long sourceId, Long propertyId, Boolean active, Pageable pageable) {
        return PageResponse.of(offerRepository.adminSearch(sourceId, propertyId, active, pageable), mapper::toDto);
    }

    @Transactional(readOnly = true)
    public OfferDto get(Long id) {
        return mapper.toDto(find(id));
    }

    /**
     * @param forceNewProperty true — zawsze tworzy nową nieruchomość (POST /admin/properties),
     *                         false — dołącza do wskazanej lub przez deduplikację (POST /admin/offers)
     */
    @Transactional
    public OfferDto create(OfferCreateRequest request, boolean forceNewProperty) {
        Source source = request.sourceId() == null
                ? sourceRepository.findByCode(MANUAL_SOURCE).orElseThrow(() -> DomainException.notFound("Źródło", MANUAL_SOURCE))
                : sourceRepository.findById(request.sourceId()).orElseThrow(() -> DomainException.notFound("Źródło", request.sourceId()));
        PropertyAttributesRequest attributes = request.attributes();
        if (request.propertyId() != null && !forceNewProperty) {
            Property property = propertyRepository.findById(request.propertyId())
                    .orElseThrow(() -> DomainException.notFound("Nieruchomość", request.propertyId()));
            attributes = attributes != null ? attributes : attributesOf(property);
        } else if (attributes == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Bez propertyId wymagane są atrybuty nieruchomości (attributes)",
                    Map.of("field", "attributes"));
        }
        NormalizedListing listing = listingFactory.build(request, attributes);
        if (offerRepository.findBySourceIdAndExternalId(source.getId(), listing.externalId()).isPresent()) {
            throw new DomainException(ErrorCode.DATA_CONFLICT,
                    "Oferta o identyfikatorze " + listing.externalId() + " już istnieje w źródle " + source.getName());
        }
        upsertService.addManual(source.getId(), listing, forceNewProperty ? null : request.propertyId(), !forceNewProperty);
        Offer offer = offerRepository.findBySourceIdAndExternalId(source.getId(), listing.externalId()).orElseThrow();
        events.publishEvent(new SearchDataChangedEvent("offer " + offer.getId() + " added"));
        return mapper.toDto(offer);
    }

    @Transactional
    public OfferDto update(Long id, OfferUpdateRequest request) {
        Offer offer = find(id);
        if (offer.getPrice().compareTo(request.price()) != 0) {
            offer.addPriceHistory(request.price(), Instant.now());
            offer.setPrice(request.price());
        }
        offer.setTitle(request.title().trim());
        offer.setDescription(request.description());
        offer.setUrl(request.url());
        offer.setActive(request.active());
        if (request.active()) {
            offer.setMissedImports(0);
        }
        offer.setPrivateSeller(request.privateSeller());
        offer.setAgencyName(request.privateSeller() ? null : request.agencyName());
        Property property = offer.getProperty();
        PropertyRecalculator.Result result = recalculator.recalculate(property, offerRepository.findByPropertyId(property.getId()));
        if (result.priceDecreased()) {
            events.publishEvent(new PriceDroppedEvent(property.getId(), null, result.priceBefore(), result.priceAfter()));
        }
        events.publishEvent(new SearchDataChangedEvent("offer " + id + " updated"));
        return mapper.toDto(offer);
    }

    @Transactional
    public void delete(Long id) {
        Offer offer = find(id);
        Property property = offer.getProperty();
        offerRepository.delete(offer);
        offerRepository.flush();
        recalculator.recalculate(property, offerRepository.findByPropertyId(property.getId()));
        events.publishEvent(new SearchDataChangedEvent("offer " + id + " deleted"));
    }

    private static PropertyAttributesRequest attributesOf(Property p) {
        return new PropertyAttributesRequest(p.getPropertyType(), p.getTransactionType(), p.getMarketType(), p.getCity().getId(),
                p.getDistrict() == null ? null : p.getDistrict().getId(), p.getStreet(),
                p.getLocation() == null ? null : p.getLocation().getY(), p.getLocation() == null ? null : p.getLocation().getX(),
                p.getArea(), p.getRooms(), p.getFloor(), p.getTotalFloors(), p.getYearBuilt(),
                p.getFeatures().stream().map(f -> f.getCode()).toList());
    }

    private Offer find(Long id) {
        return offerRepository.findById(id).orElseThrow(() -> DomainException.notFound("Oferta", id));
    }
}
