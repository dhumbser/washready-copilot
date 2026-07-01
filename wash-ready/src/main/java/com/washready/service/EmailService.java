package com.washready.service;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.InternetAddress;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String defaultFrom;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from:}") String defaultFrom,
                        @Value("${spring.mail.username:}") String username) {
        this.mailSender = mailSender;
        this.defaultFrom = (defaultFrom == null || defaultFrom.isBlank()) ? username : defaultFrom;
    }

    public record Email(String to, String subject, String body, boolean html) {}

    public void send(Email m) {
        try {
            InternetAddress.parse(m.to(), true); // valida formato
            var msg = mailSender.createMimeMessage();
            var h = new MimeMessageHelper(msg, false, StandardCharsets.UTF_8.name());
            h.setFrom(new InternetAddress(defaultFrom)); // Gmail suele exigir from = username/app
            h.setTo(m.to());
            h.setSubject(m.subject());
            h.setText(m.body(), m.html());
            mailSender.send(msg);
        } catch (Exception e) {
            // Desenrolla para ver la causa raíz en el 500
            Throwable root = e;
            while (root.getCause() != null) root = root.getCause();
            String detail = (root.getMessage() != null) ? root.getMessage() : root.getClass().getSimpleName();
            throw new IllegalStateException("No se pudo enviar el correo: " + detail, e);
        }
    }

}
