package com.jobera.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean enabled;

    public SmtpEmailService(JavaMailSender mailSender,
                            @Value("${spring.mail.host:}") String mailHost,
                            @Value("${app.mail.from:no-reply@dreamrole.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.enabled = mailHost != null && !mailHost.isBlank();
        if (!enabled) {
            log.info("No spring.mail.host configured; outgoing email will be logged, not sent. "
                + "Set SMTP_HOST (and SMTP_USERNAME/SMTP_PASSWORD if needed) to send real email.");
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void send(String to, String subject, String body) {
        if (!enabled) {
            log.info("[email:dev-mode] to={} subject=\"{}\"\n{}", to, subject, body);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
