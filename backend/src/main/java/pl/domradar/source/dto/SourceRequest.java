package pl.domradar.source.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import pl.domradar.source.validation.ValidCron;

@Schema(description = "Dane źródła do utworzenia lub aktualizacji")
public record SourceRequest(
        @Schema(example = "domiporta", description = "Unikalny kod: małe litery, cyfry i myślniki")
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[a-z0-9-]+$", message = "dozwolone są małe litery, cyfry i myślniki")
        String code,

        @Schema(example = "Domiporta") @NotBlank @Size(max = 100) String name,

        @Schema(example = "https://www.domiporta.pl") @NotBlank @URL @Size(max = 500) String baseUrl,

        @Schema(example = "demo", description = "Kod adaptera: demo, snapshot, otodom-http")
        @NotBlank @Size(max = 50) String adapter,

        @Schema(example = "true") boolean enabled,

        @Schema(example = "0 0 */6 * * *", description = "Wyrażenie cron Springa (6 pól) lub puste = brak automatycznych importów")
        @ValidCron @Size(max = 100) String importCron) {
}
