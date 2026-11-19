package pl.domradar.notification.websocket;

import pl.domradar.auth.CurrentUser;

import java.security.Principal;

/** Principal sesji STOMP; nazwa = id użytkownika, więc convertAndSendToUser(userId, ...) trafia do właściwej sesji. */
public record StompPrincipal(CurrentUser user) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(user.id());
    }
}
