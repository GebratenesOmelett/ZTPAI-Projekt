package pl.domradar.source.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Źródło ogłoszeń (portal)")
public record SourceDto(
        @Schema(example = "1") Long id,
        @Schema(example = "otodom") String code,
        @Schema(example = "Otodom") String name,
        @Schema(example = "https://www.otodom.pl") String baseUrl,
        @Schema(example = "demo") String adapter,
        boolean enabled,
        @Schema(example = "0 0 */6 * * *") String importCron,
        Instant lastImportAt,
        Instant createdAt) {
}
