package pl.domradar.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import pl.domradar.user.Role;

import java.util.List;

/** Zalogowany użytkownik — principal w SecurityContext (wstrzykiwany przez @AuthenticationPrincipal). */
public record CurrentUser(Long id, String email, Role role) {

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
