package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.entity.Webhook;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.WebhookRepository;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock WebhookRepository webhookRepository;
    @Mock RecruiterRepository recruiterRepository;
    @Mock RestTemplate restTemplate;

    WebhookService service;

    User owner;
    Recruiter recruiter;

    @BeforeEach
    void setUp() {
        service = new WebhookService(webhookRepository, recruiterRepository, restTemplate);
        owner = new User();
        owner.setId(1L);
        recruiter = new Recruiter();
        recruiter.setId(100L);
        recruiter.setUser(owner);
        lenient().when(recruiterRepository.findById(100L)).thenReturn(Optional.of(recruiter));
        lenient().when(webhookRepository.save(any())).thenAnswer(inv -> {
            Webhook w = inv.getArgument(0);
            w.setId(700L);
            return w;
        });
    }

    @Test
    void ownerCanRegisterAWebhook() {
        Map<String, Object> result = service.register(100L, 1L, "https://example.com/hook", Webhook.EVENT_APPLICATION_SUBMITTED);

        assertEquals("https://example.com/hook", result.get("url"));
        assertEquals(Webhook.EVENT_APPLICATION_SUBMITTED, result.get("eventType"));
    }

    @Test
    void nonOwnerCannotRegisterAWebhook() {
        assertThrows(WebhookService.NotTheAccountOwnerException.class,
            () -> service.register(100L, 2L, "https://example.com/hook", Webhook.EVENT_APPLICATION_SUBMITTED));
    }

    @Test
    void unknownEventTypeIsRejected() {
        assertThrows(WebhookService.InvalidEventTypeException.class,
            () -> service.register(100L, 1L, "https://example.com/hook", "SOMETHING_ELSE"));
    }

    @Test
    void dispatchPostsASignedPayloadToEachActiveMatchingWebhook() {
        Webhook webhook = new Webhook(100L, "https://example.com/hook", Webhook.EVENT_APPLICATION_SUBMITTED, "my-secret");
        when(webhookRepository.findByRecruiterIdAndEventTypeAndActiveTrue(100L, Webhook.EVENT_APPLICATION_SUBMITTED))
            .thenReturn(List.of(webhook));

        service.dispatch(100L, Webhook.EVENT_APPLICATION_SUBMITTED, Map.of("applicationId", 5));

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(org.mockito.ArgumentMatchers.eq("https://example.com/hook"), captor.capture(), org.mockito.ArgumentMatchers.eq(String.class));
        assertEquals(true, captor.getValue().getHeaders().getFirst("X-DreamRole-Signature").startsWith("sha256="));
    }

    @Test
    void dispatchDoesNothingWhenNoWebhooksAreRegistered() {
        when(webhookRepository.findByRecruiterIdAndEventTypeAndActiveTrue(100L, Webhook.EVENT_APPLICATION_SUBMITTED))
            .thenReturn(List.of());

        service.dispatch(100L, Webhook.EVENT_APPLICATION_SUBMITTED, Map.of());

        verify(restTemplate, never()).postForEntity(any(String.class), any(), any(Class.class));
    }

    @Test
    void dispatchSwallowsDeliveryFailuresWithoutThrowing() {
        Webhook webhook = new Webhook(100L, "https://example.com/hook", Webhook.EVENT_APPLICATION_SUBMITTED, "secret");
        when(webhookRepository.findByRecruiterIdAndEventTypeAndActiveTrue(100L, Webhook.EVENT_APPLICATION_SUBMITTED))
            .thenReturn(List.of(webhook));
        when(restTemplate.postForEntity(any(String.class), any(), any(Class.class)))
            .thenThrow(new RuntimeException("connection refused"));

        service.dispatch(100L, Webhook.EVENT_APPLICATION_SUBMITTED, Map.of());
    }

    @Test
    void dispatchDoesNothingForANullRecruiterId() {
        service.dispatch(null, Webhook.EVENT_APPLICATION_SUBMITTED, Map.of());

        verify(webhookRepository, never()).findByRecruiterIdAndEventTypeAndActiveTrue(any(), any());
    }

    @Test
    void ownerCanDeleteAWebhook() {
        Webhook webhook = new Webhook(100L, "https://example.com/hook", Webhook.EVENT_APPLICATION_SUBMITTED, "secret");
        webhook.setId(700L);
        when(webhookRepository.findByIdAndRecruiterId(700L, 100L)).thenReturn(Optional.of(webhook));

        service.delete(100L, 700L, 1L);

        verify(webhookRepository).delete(webhook);
    }

    @Test
    void ownerCanRegisterInterviewAndTestWebhooks() {
        Map<String, Object> interviewWebhook = service.register(100L, 1L, "https://hooks.slack.com/services/T1/B2/C3", Webhook.EVENT_INTERVIEW_SCHEDULED);
        assertEquals(Webhook.EVENT_INTERVIEW_SCHEDULED, interviewWebhook.get("eventType"));

        Map<String, Object> testWebhook = service.register(100L, 1L, "https://discord.com/api/webhooks/1/2", Webhook.EVENT_TEST_COMPLETED);
        assertEquals(Webhook.EVENT_TEST_COMPLETED, testWebhook.get("eventType"));
    }

    @Test
    void dispatchFormatsSlackBlockKitForSlackUrls() {
        Webhook webhook = new Webhook(100L, "https://hooks.slack.com/services/T1/B2/C3", Webhook.EVENT_INTERVIEW_SCHEDULED, "slack-secret");
        when(webhookRepository.findByRecruiterIdAndEventTypeAndActiveTrue(100L, Webhook.EVENT_INTERVIEW_SCHEDULED))
            .thenReturn(List.of(webhook));

        service.dispatch(100L, Webhook.EVENT_INTERVIEW_SCHEDULED, Map.of("candidate", "Alice", "role", "Staff Architect"));

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(org.mockito.ArgumentMatchers.eq("https://hooks.slack.com/services/T1/B2/C3"), captor.capture(), org.mockito.ArgumentMatchers.eq(String.class));
        String body = captor.getValue().getBody().toString();
        assertEquals(true, body.contains("\"blocks\""));
        assertEquals(true, body.contains("DreamRole Notification"));
    }

    @Test
    void dispatchFormatsDiscordEmbedForDiscordUrls() {
        Webhook webhook = new Webhook(100L, "https://discord.com/api/webhooks/999/xyz", Webhook.EVENT_APPLICATION_SUBMITTED, "discord-secret");
        when(webhookRepository.findByRecruiterIdAndEventTypeAndActiveTrue(100L, Webhook.EVENT_APPLICATION_SUBMITTED))
            .thenReturn(List.of(webhook));

        service.dispatch(100L, Webhook.EVENT_APPLICATION_SUBMITTED, Map.of("candidate", "Bob", "role", "Lead Designer"));

        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(org.mockito.ArgumentMatchers.eq("https://discord.com/api/webhooks/999/xyz"), captor.capture(), org.mockito.ArgumentMatchers.eq(String.class));
        String body = captor.getValue().getBody().toString();
        assertEquals(true, body.contains("\"embeds\""));
        assertEquals(true, body.contains("DreamRole Hiring Event"));
    }

    @Test
    void testDispatchSendsPingAndReturnsSuccess() {
        Webhook webhook = new Webhook(100L, "https://example.com/test-hook", Webhook.EVENT_APPLICATION_SUBMITTED, "test-secret");
        webhook.setId(700L);
        when(webhookRepository.findByIdAndRecruiterId(700L, 100L)).thenReturn(Optional.of(webhook));

        Map<String, Object> result = service.testDispatch(100L, 700L, 1L);

        assertEquals("SUCCESS", result.get("status"));
        ArgumentCaptor<HttpEntity> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(org.mockito.ArgumentMatchers.eq("https://example.com/test-hook"), captor.capture(), org.mockito.ArgumentMatchers.eq(String.class));
    }
}
