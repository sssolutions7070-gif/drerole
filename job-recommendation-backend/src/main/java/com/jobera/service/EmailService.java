package com.jobera.service;

/**
 * Transport for outgoing email. When no SMTP host is configured (the default for local
 * development) messages are written to the application log instead of being sent, so the
 * rest of the app can call this unconditionally without needing real mail credentials.
 */
public interface EmailService {
    void send(String to, String subject, String body);

    boolean isEnabled();
}
