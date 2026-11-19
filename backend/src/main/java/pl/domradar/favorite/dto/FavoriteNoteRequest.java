package pl.domradar.favorite.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "Zmiana notatki (null lub pusty tekst usuwa notatkę)")
public record FavoriteNoteRequest(@Schema(example = "Cena do negocjacji") @Size(max = 2000) String note) {
}
