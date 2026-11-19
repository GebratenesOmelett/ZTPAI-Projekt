package pl.domradar.notification.mail;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

/**
 * Wysyłka e-maili z alertami (w środowisku dev do Mailpit, podgląd: http://localhost:8025).
 * Asynchronicznie — błąd serwera SMTP nie blokuje importu ani dopasowania alertów.
 */
@Slf4j
@Service
public class MailService {

    public record ListingLine(Long propertyId, String title, BigDecimal price, String location) {
    }

    private final JavaMailSender mailSender;
    private final String from;
    private final String publicUrl;

    public MailService(JavaMailSender mailSender, @Value("${app.mail.from}") String from,
                       @Value("${app.public-url}") String publicUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Async
    public void sendAlert(String to, String displayName, String subject, String intro, List<ListingLine> listings, Long savedSearchId) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html(displayName, intro, listings, savedSearchId), true);
            mailSender.send(message);
            log.info("Alert e-mail sent to {}", to);
        } catch (Exception e) {
            log.warn("Cannot send alert e-mail to {}: {}", to, e.getMessage());
        }
    }

    String html(String displayName, String intro, List<ListingLine> listings, Long savedSearchId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html lang=\"pl\"><body style=\"font-family:Arial,sans-serif;color:#1f2937\">");
        sb.append("<h2 style=\"color:#0f766e\">DomRadar</h2>");
        sb.append("<p>Cześć ").append(esc(displayName)).append(",</p>");
        sb.append("<p>").append(esc(intro)).append("</p><ul>");
        for (ListingLine l : listings) {
            sb.append("<li style=\"margin-bottom:8px\"><a href=\"").append(publicUrl).append("/nieruchomosci/").append(l.propertyId())
                    .append("\">").append(esc(l.title())).append("</a><br><strong>").append(formatPrice(l.price()))
                    .append("</strong> · ").append(esc(l.location())).append("</li>");
        }
        sb.append("</ul>");
        if (savedSearchId != null) {
            sb.append("<p><a href=\"").append(publicUrl).append("/moje-wyszukiwania\">Zarządzaj alertami</a></p>");
        }
        sb.append("<p style=\"font-size:12px;color:#6b7280\">Wiadomość wygenerowana automatycznie przez DomRadar (dane demonstracyjne).</p>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }

    static String formatPrice(BigDecimal price) {
        if (price == null) {
            return "cena do uzgodnienia";
        }
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(new Locale("pl", "PL"));
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0", symbols).format(price) + " zł";
    }
}
