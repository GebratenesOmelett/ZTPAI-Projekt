package pl.domradar.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.web.PageResponse;
import pl.domradar.notification.dto.NotificationDto;
import pl.domradar.notification.event.NotificationCreatedEvent;
import pl.domradar.user.UserRepository;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final UserRepository userRepository;
    private final NotificationMapper mapper;
    private final ApplicationEventPublisher events;

    /** Zapisuje powiadomienie; push przez WebSocket następuje po zatwierdzeniu transakcji. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NotificationDto create(Long userId, NotificationType type, String title, Map<String, Object> payload) {
        Notification n = repository.save(new Notification(userRepository.getReferenceById(userId), type,
                title.length() > 300 ? title.substring(0, 300) : title, payload));
        NotificationDto dto = mapper.toDto(n);
        events.publishEvent(new NotificationCreatedEvent(userId, dto));
        return dto;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(Long userId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        return PageResponse.of(unreadOnly
                ? repository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable)
                : repository.findByUserIdOrderByCreatedAtDesc(userId, pageable), mapper::toDto);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public NotificationDto setRead(Long userId, Long id, boolean read) {
        Notification n = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> DomainException.notFound("Powiadomienie", id));
        n.setRead(read);
        return mapper.toDto(n);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId);
    }
}
