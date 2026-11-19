package pl.domradar.property.admin;

import org.junit.jupiter.api.Test;
import pl.domradar.common.exception.DomainException;
import pl.domradar.common.exception.ErrorCode;
import pl.domradar.property.Property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reguła biznesowa nr 7 — edycja wymaga aktualnej wersji nieruchomości. */
class PropertyVersionCheckTest {

    private static Property property(long version) {
        Property p = new Property();
        p.setId(10L);
        p.setVersion(version);
        return p;
    }

    @Test
    void matchingVersionIsAccepted() {
        assertThatCode(() -> PropertyAdminService.checkVersion(property(3), 3L)).doesNotThrowAnyException();
    }

    @Test
    void staleOrMissingVersionIsRejectedWithConflict() {
        assertThatThrownBy(() -> PropertyAdminService.checkVersion(property(4), 3L))
                .isInstanceOf(DomainException.class)
                .satisfies(e -> {
                    DomainException de = (DomainException) e;
                    assertThat(de.getCode()).isEqualTo(ErrorCode.VERSION_CONFLICT);
                    assertThat(de.getDetails()).containsEntry("currentVersion", 4L);
                });
        assertThatThrownBy(() -> PropertyAdminService.checkVersion(property(4), null))
                .satisfies(e -> assertThat(((DomainException) e).getCode()).isEqualTo(ErrorCode.VERSION_CONFLICT));
    }
}
