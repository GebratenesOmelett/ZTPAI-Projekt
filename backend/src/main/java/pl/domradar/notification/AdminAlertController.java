package pl.domradar.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.domradar.notification.alert.DailyDigestService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/alerts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin: alerty", description = "Ręczne uruchomienie dziennego digestu (normalnie scheduler o 7:00)")
public class AdminAlertController {

    private final DailyDigestService dailyDigestService;

    @PostMapping("/daily-digest")
    @Operation(summary = "Uruchom dzienny digest teraz (np. na potrzeby demonstracji)")
    public Map<String, Integer> runDailyDigest() {
        return Map.of("sent", dailyDigestService.runDigest());
    }
}
