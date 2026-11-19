package pl.domradar.property;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.favorite.FavoriteRepository;
import pl.domradar.property.dictionary.dto.FeatureDto;
import pl.domradar.property.dto.PropertyDetailsDto;
import pl.domradar.search.stats.DistrictStatsService;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

/** Odczyt szczegółów nieruchomości (z ofertami, historią cen i porównaniem do mediany) oraz porównywarka. */
@Service
@RequiredArgsConstructor
public class PropertyQueryService {

    public static final int MAX_COMPARE = 4;

    private final PropertyRepository propertyRepository;
    private final OfferRepository offerRepository;
    private final FavoriteRepository favoriteRepository;
    private final DistrictStatsService statsService;
    private final OfferMapper offerMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PropertyDetailsDto details(Long id, Long currentUserId) {
        Property p = propertyRepository.findDetailedById(id).orElseThrow(() -> DomainException.notFound("Nieruchomość", id));
        return toDetails(p, currentUserId);
    }

    @Transactional(readOnly = true)
    public List<PropertyDetailsDto> compare(List<Long> ids, Long currentUserId) {
        List<Long> unique = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (unique.isEmpty() || unique.size() > MAX_COMPARE) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "Porównać można od 1 do " + MAX_COMPARE + " nieruchomości",
                    Map.of("field", "ids"));
        }
        return unique.stream().map(id -> details(id, currentUserId)).toList();
    }

    public PropertyDetailsDto toDetails(Property p, Long currentUserId) {
        List<Offer> offers = offerRepository.findByPropertyIdOrderByPriceAsc(p.getId());
        Offer representative = offers.stream().filter(Offer::isActive).findFirst().orElse(offers.isEmpty() ? null : offers.getFirst());
        LinkedHashSet<String> photos = new LinkedHashSet<>();
        offers.stream().sorted(Comparator.comparing((Offer o) -> !o.isActive())).forEach(o -> o.getPhotos().forEach(ph -> photos.add(ph.getUrl())));
        List<FeatureDto> features = p.getFeatures().stream().sorted(Comparator.comparing(f -> f.getName()))
                .map(f -> new FeatureDto(f.getCode(), f.getName())).toList();
        PropertyDetailsDto.MedianComparisonDto median = statsService
                .compare(p.getDistrict() == null ? null : p.getDistrict().getId(), p.getPropertyType(), p.getTransactionType(), p.getPricePerM2())
                .map(m -> new PropertyDetailsDto.MedianComparisonDto(m.districtMedianPricePerM2(), m.vsMedianPercent(),
                        m.sampleSize(), label(m.vsMedianPercent())))
                .orElse(null);
        Long favoriteId = currentUserId == null ? null
                : favoriteRepository.findByUserIdAndPropertyId(currentUserId, p.getId()).map(f -> f.getId()).orElse(null);
        return new PropertyDetailsDto(
                p.getId(), p.getTitle(), representative == null ? null : representative.getDescription(),
                p.getPropertyType(), p.getTransactionType(), p.getMarketType(),
                p.getCity().getId(), p.getCity().getName(),
                p.getDistrict() == null ? null : p.getDistrict().getId(), p.getDistrict() == null ? null : p.getDistrict().getName(),
                p.getStreet(),
                p.getLocation() == null ? null : p.getLocation().getY(), p.getLocation() == null ? null : p.getLocation().getX(),
                p.getArea(), p.getRooms(), p.getFloor(), p.getTotalFloors(), p.getYearBuilt(),
                p.getCurrentPrice(), p.getPricePerM2(), p.isPriceDropped(), p.getPriceDropPercent(),
                p.getLastPriceChangeAt(), p.getLastPriceDropAt(), p.getActiveSourceCount(),
                p.getFirstSeenAt(), p.getLastSeenAt(),
                Math.max(0, Duration.between(p.getFirstSeenAt(), clock.instant()).toDays()),
                p.getStatus(), features, photos.stream().limit(20).toList(),
                offers.stream().map(offerMapper::toDto).toList(), median, p.getVersion(), favoriteId);
    }

    static String label(BigDecimal pct) {
        if (pct == null) {
            return null;
        }
        DecimalFormat df = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(new Locale("pl", "PL")));
        if (pct.signum() == 0) {
            return "Cena równa medianie dzielnicy";
        }
        return df.format(pct.abs()) + "% " + (pct.signum() < 0 ? "poniżej" : "powyżej") + " mediany dzielnicy";
    }
}
