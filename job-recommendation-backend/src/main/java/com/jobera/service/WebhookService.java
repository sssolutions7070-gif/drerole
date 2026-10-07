package com.jobera.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobera.entity.Recruiter;
import com.jobera.entity.Webhook;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.WebhookRepository;

/**
 * Recruiter-registered URLs that get an HMAC-signed POST when a chosen event happens on their
 * jobs (a new application, an accepted offer), so an external system (their own ATS, Zapier)
 * doesn't have to poll the API. Delivery is fire-and-forget and best-effort - there's no retry
 * queue or delivery log, matching this app's existing RealtimeNotifier (push if reachable, don't
 * block the request that triggered it if not).
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int SECRET_RANDOM_BYTES = 24;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> VALID_EVENT_TYPES = Set.of(
        Webhook.EVENT_APPLICATION_SUBMITTED,
        Webhook.EVENT_OFFER_ACCEPTED,
        Webhook.EVENT_INTERVIEW_SCHEDULED,
        Webhook.EVENT_TEST_COMPLETED);

    private final WebhookRepository webhookRepository;
    private final RecruiterRepository recruiterRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WebhookService(WebhookRepository webhookRepository, RecruiterRepository recruiterRepository,
                           RestTemplate restTemplate) {
        this.webhookRepository = webhookRepository;
        this.recruiterRepository = recruiterRepository;
        this.restTemplate = restTemplate;
    }

    public static class RecruiterNotFoundException extends RuntimeException {
        public RecruiterNotFoundException(Long recruiterId) {
            super("Recruiter not found: " + recruiterId);
        }
    }

    public static class NotTheAccountOwnerException extends RuntimeException {
        public NotTheAccountOwnerException() {
            super("Only the account owner can manage webhooks");
        }
    }

    public static class InvalidEventTypeException extends RuntimeException {
        public InvalidEventTypeException(String eventType) {
            super("Unknown webhook event type: " + eventType);
        }
    }

    public static class WebhookNotFoundException extends RuntimeException {
        public WebhookNotFoundException(Long webhookId) {
            super("Webhook not found: " + webhookId);
        }
    }

    private void requireOwner(Recruiter recruiter, Long callerId) {
        if (recruiter.getUser() == null || !recruiter.getUser().getId().equals(callerId)) {
            throw new NotTheAccountOwnerException();
        }
    }

    /** Returns the signing secret under "secret" - the only time it's ever shown in full. */
    public Map<String, Object> register(Long recruiterId, Long callerId, String url, String eventType) {
        Recruiter recruiter = recruiterRepository.findById(recruiterId)
            .orElseThrow(() -> new RecruiterNotFoundException(recruiterId));
        requireOwner(recruiter, callerId);

        if (!VALID_EVENT_TYPES.contains(eventType)) {
            throw new InvalidEventTypeException(eventType);
        }

        byte[] randomBytes = new byte[SECRET_RANDOM_BYTES];
        RANDOM.nextBytes(randomBytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        Webhook saved = webhookRepository.save(new Webhook(recruiterId, url, eventType, secret));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", saved.getId());
        response.put("url", saved.getUrl());
        response.put("eventType", saved.getEventType());
        response.put("secret", saved.getSecret());
        response.put("createdAt", saved.getCreatedAt());
        return response;
    }

    public List<Map<String, Object>> list(Long recruiterId) {
        return webhookRepository.findByRecruiterIdOrderByCreatedAtDesc(recruiterId).stream()
            .map(this::view)
            .toList();
    }

    private Map<String, Object> view(Webhook webhook) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", webhook.getId());
        view.put("url", webhook.getUrl());
        view.put("eventType", webhook.getEventType());
        view.put("createdAt", webhook.getCreatedAt());
        view.put("active", webhook.isActive());
        return view;
    }

    public void delete(Long recruiterId, Long webhookId, Long callerId) {
        Recruiter recruiter = recruiterRepository.findById(recruiterId)
            .orElseThrow(() -> new RecruiterNotFoundException(recruiterId));
        requireOwner(recruiter, callerId);

        Webhook webhook = webhookRepository.findByIdAndRecruiterId(webhookId, recruiterId)
            .orElseThrow(() -> new WebhookNotFoundException(webhookId));
        webhookRepository.delete(webhook);
    }

    /** Formats the payload according to destination platform (Slack Block Kit, Discord Embeds, or standard JSON). */
    public String formatPayload(String url, String eventType, Map<String, Object> payload) {
        try {
            if (url != null && url.toLowerCase().contains("hooks.slack.com")) {
                Map<String, Object> slackBody = new LinkedHashMap<>();
                String title = "DreamRole Notification: " + humanizeEvent(eventType);
                slackBody.put("text", title);
                java.util.List<Map<String, Object>> blocks = new java.util.ArrayList<>();
                blocks.add(Map.of("type", "header", "text", Map.of("type", "plain_text", "text", "🎯 " + title, "emoji", true)));

                StringBuilder mrkdwn = new StringBuilder();
                mrkdwn.append("*Event:* `").append(eventType).append("`\n");
                for (Map.Entry<String, Object> entry : payload.entrySet()) {
                    mrkdwn.append("*").append(entry.getKey()).append(":* ").append(entry.getValue()).append("\n");
                }
                blocks.add(Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", mrkdwn.toString())));
                slackBody.put("blocks", blocks);
                return objectMapper.writeValueAsString(slackBody);
            } else if (url != null && (url.toLowerCase().contains("discord.com/api/webhooks") || url.toLowerCase().contains("discordapp.com/api/webhooks"))) {
                Map<String, Object> discordBody = new LinkedHashMap<>();
                discordBody.put("content", "🎯 **DreamRole Alert: " + humanizeEvent(eventType) + "**");
                Map<String, Object> embed = new LinkedHashMap<>();
                embed.put("title", "DreamRole Hiring Event");
                embed.put("description", "An automated update from your DreamRole workspace.");
                embed.put("color", 5793266); // Blurple

                java.util.List<Map<String, Object>> fields = new java.util.ArrayList<>();
                fields.add(Map.of("name", "Event", "value", eventType, "inline", true));
                for (Map.Entry<String, Object> entry : payload.entrySet()) {
                    fields.add(Map.of("name", entry.getKey(), "value", String.valueOf(entry.getValue()), "inline", true));
                }
                embed.put("fields", fields);
                discordBody.put("embeds", java.util.List.of(embed));
                return objectMapper.writeValueAsString(discordBody);
            } else {
                return objectMapper.writeValueAsString(Map.of("event", eventType, "data", payload));
            }
        } catch (Exception e) {
            log.warn("Payload formatting fallback for event {}: {}", eventType, e.getMessage());
            return "{\"event\":\"" + eventType + "\"}";
        }
    }

    private String humanizeEvent(String eventType) {
        if (eventType == null) return "Hiring Event";
        return switch (eventType) {
            case Webhook.EVENT_APPLICATION_SUBMITTED -> "New Candidate Application";
            case Webhook.EVENT_OFFER_ACCEPTED -> "Candidate Accepted Offer";
            case Webhook.EVENT_INTERVIEW_SCHEDULED -> "Interview Confirmed";
            case Webhook.EVENT_TEST_COMPLETED -> "Assessment Completed";
            default -> eventType.replace('_', ' ');
        };
    }

    /** Test dispatch sends an immediate verification message to test connection. */
    public Map<String, Object> testDispatch(Long recruiterId, Long webhookId, Long callerId) {
        Recruiter recruiter = recruiterRepository.findById(recruiterId)
            .orElseThrow(() -> new RecruiterNotFoundException(recruiterId));
        requireOwner(recruiter, callerId);

        Webhook webhook = webhookRepository.findByIdAndRecruiterId(webhookId, recruiterId)
            .orElseThrow(() -> new WebhookNotFoundException(webhookId));

        Map<String, Object> testData = new LinkedHashMap<>();
        testData.put("status", "VERIFIED");
        testData.put("testPing", true);
        testData.put("message", "✨ DreamRole test webhook ping successful! Your integration is active.");
        testData.put("eventType", webhook.getEventType());
        testData.put("timestamp", java.time.LocalDateTime.now().toString());

        String body = formatPayload(webhook.getUrl(), webhook.getEventType(), testData);
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-DreamRole-Signature", "sha256=" + hmacHex(body, webhook.getSecret()));
            restTemplate.postForEntity(webhook.getUrl(), new HttpEntity<>(body, headers), String.class);
            return Map.of("status", "SUCCESS", "message", "Test ping delivered successfully to " + webhook.getUrl());
        } catch (Exception e) {
            return Map.of("status", "FAILED", "message", "Delivery failed: " + e.getMessage());
        }
    }

    /** Fires every active webhook a recruiter has registered for this event type. Never throws. */
    public void dispatch(Long recruiterId, String eventType, Map<String, Object> payload) {
        if (recruiterId == null) {
            return;
        }
        List<Webhook> webhooks = webhookRepository.findByRecruiterIdAndEventTypeAndActiveTrue(recruiterId, eventType);
        if (webhooks.isEmpty()) {
            return;
        }

        for (Webhook webhook : webhooks) {
            try {
                String body = formatPayload(webhook.getUrl(), eventType, payload);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-DreamRole-Signature", "sha256=" + hmacHex(body, webhook.getSecret()));
                restTemplate.postForEntity(webhook.getUrl(), new HttpEntity<>(body, headers), String.class);
            } catch (Exception e) {
                log.debug("Webhook delivery failed for recruiter {} event {}: {}", recruiterId, eventType, e.getMessage());
            }
        }
    }

    private String hmacHex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to compute webhook signature", e);
        }
    }
}
