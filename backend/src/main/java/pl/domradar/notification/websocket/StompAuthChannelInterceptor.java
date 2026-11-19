package pl.domradar.notification.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import pl.domradar.auth.CurrentUser;
import pl.domradar.auth.JwtService;
import pl.domradar.user.User;
import pl.domradar.user.UserRepository;

import java.security.Principal;

/**
 * Uwierzytelnianie WebSocket/STOMP: klient wysyła access token JWT w nagłówku {@code Authorization}
 * ramki CONNECT. Subskrypcja kanałów {@code /topic/admin/**} wymaga roli ADMIN,
 * kanały {@code /user/**} — zalogowanego użytkownika.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (accessor.getCommand() == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            authorizeSubscription(accessor.getDestination(), accessor.getUser());
        }
        return message;
    }

    private Principal authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Brak tokenu uwierzytelniającego");
        }
        CurrentUser claims = jwtService.parse(header.substring(7).trim())
                .orElseThrow(() -> new MessageDeliveryException("Nieprawidłowy lub wygasły token"));
        User user = userRepository.findById(claims.id()).filter(User::isEnabled)
                .orElseThrow(() -> new MessageDeliveryException("Konto nieaktywne"));
        CurrentUser current = new CurrentUser(user.getId(), user.getEmail(), user.getRole());
        return new UsernamePasswordAuthenticationToken(new StompPrincipal(current), null, current.authorities());
    }

    private static void authorizeSubscription(String destination, Principal principal) {
        if (destination == null) {
            return;
        }
        CurrentUser user = principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof StompPrincipal sp ? sp.user() : null;
        if (destination.startsWith("/topic/admin") && (user == null || !user.isAdmin())) {
            throw new MessageDeliveryException("Kanał dostępny tylko dla administratora");
        }
        if (destination.startsWith("/user/") && user == null) {
            throw new MessageDeliveryException("Wymagane uwierzytelnienie");
        }
    }
}
