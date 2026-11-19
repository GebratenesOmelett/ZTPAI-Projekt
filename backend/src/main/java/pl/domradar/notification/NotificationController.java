package pl.domradar.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pl.domradar.auth.CurrentUser;
import pl.domradar.common.web.PageResponse;
import pl.domradar.notification.dto.NotificationDto;
import pl.domradar.notification.dto.NotificationPatchRequest;
import pl.domradar.notification.dto.UnreadCountDto;

@Validated
@RestController
@RequestMapping("/api/v1/me/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Powiadomienia", description = "Powiadomienia o alertach (na żywo także przez WebSocket /user/queue/notifications)")
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    @Operation(summary = "Lista powiadomień (od najnowszych)")
    public PageResponse<NotificationDto> list(@AuthenticationPrincipal CurrentUser user,
                                              @RequestParam(defaultValue = "false") boolean unreadOnly,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(user.id(), unreadOnly, page, size);
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Liczba nieprzeczytanych (dzwonek)")
    public UnreadCountDto unreadCount(@AuthenticationPrincipal CurrentUser user) {
        return new UnreadCountDto(service.unreadCount(user.id()));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Oznaczenie powiadomienia jako przeczytane / nieprzeczytane")
    public NotificationDto patch(@AuthenticationPrincipal CurrentUser user, @PathVariable Long id,
                                 @Valid @RequestBody NotificationPatchRequest request) {
        return service.setRead(user.id(), id, request.read());
    }

    @PatchMapping
    @Operation(summary = "Oznaczenie wszystkich powiadomień jako przeczytane")
    public UnreadCountDto markAllRead(@AuthenticationPrincipal CurrentUser user) {
        service.markAllRead(user.id());
        return new UnreadCountDto(service.unreadCount(user.id()));
    }
}
