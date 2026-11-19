package pl.domradar.favorite;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.common.web.PageResponse;
import pl.domradar.favorite.dto.FavoriteDto;
import pl.domradar.property.Property;
import pl.domradar.property.PropertyRepository;
import pl.domradar.user.UserRepository;

import java.util.Map;

/**
 * Ulubione nieruchomości użytkownika z notatkami. Zarchiwizowane nieruchomości pozostają w ulubionych
 * z oznaczeniem (reguła nr 3), mimo że domyślnie znikają z wyszukiwania.
 */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final FavoriteMapper mapper;

    @Transactional(readOnly = true)
    public PageResponse<FavoriteDto> list(Long userId, Pageable pageable) {
        return PageResponse.of(favoriteRepository.findByUserId(userId, pageable), mapper::toDto);
    }

    @Transactional
    public FavoriteDto add(Long userId, Long propertyId, String note) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> DomainException.notFound("Nieruchomość", propertyId));
        if (favoriteRepository.existsByUserIdAndPropertyId(userId, propertyId)) {
            throw exists(propertyId);
        }
        try {
            Favorite favorite = favoriteRepository.saveAndFlush(
                    new Favorite(userRepository.getReferenceById(userId), property, blankToNull(note)));
            return mapper.toDto(favorite);
        } catch (DataIntegrityViolationException e) {
            throw exists(propertyId);
        }
    }

    @Transactional
    public FavoriteDto updateNote(Long userId, Long favoriteId, String note) {
        Favorite favorite = find(userId, favoriteId);
        favorite.setNote(blankToNull(note));
        return mapper.toDto(favorite);
    }

    @Transactional
    public void remove(Long userId, Long favoriteId) {
        favoriteRepository.delete(find(userId, favoriteId));
    }

    private Favorite find(Long userId, Long favoriteId) {
        return favoriteRepository.findByIdAndUserId(favoriteId, userId)
                .orElseThrow(() -> DomainException.notFound("Ulubione", favoriteId));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static DomainException exists(Long propertyId) {
        return new DomainException(ErrorCode.FAVORITE_EXISTS, "Ta nieruchomość jest już w ulubionych",
                Map.of("propertyId", propertyId));
    }
}
