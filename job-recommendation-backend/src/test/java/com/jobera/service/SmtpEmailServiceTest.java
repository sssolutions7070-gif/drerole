package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class SmtpEmailServiceTest {

    @Mock JavaMailSender mailSender;

    @Test
    void withNoHostConfiguredItLogsInsteadOfSending() {
        SmtpEmailService service = new SmtpEmailService(mailSender, "", "no-reply@dreamrole.local");

        assertFalse(service.isEnabled());
        service.send("a@b.com", "Subject", "Body");

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void withAHostConfiguredItSendsThroughJavaMailSender() {
        SmtpEmailService service = new SmtpEmailService(mailSender, "smtp.example.com", "no-reply@dreamrole.local");

        assertTrue(service.isEnabled());
        service.send("a@b.com", "Subject", "Body");

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void aSendFailureIsSwallowedNotThrown() {
        SmtpEmailService service = new SmtpEmailService(mailSender, "smtp.example.com", "no-reply@dreamrole.local");
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("boom"))
            .when(mailSender).send(any(SimpleMailMessage.class));

        service.send("a@b.com", "Subject", "Body");
    }
}
