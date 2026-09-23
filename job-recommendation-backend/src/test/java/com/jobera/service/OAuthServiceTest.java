package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.jobera.service.OAuthService.OAuthException;
import com.jobera.service.OAuthService.OAuthUserInfo;

@ExtendWith(MockitoExtension.class)
class OAuthServiceTest {

    @Mock RestTemplate restTemplate;

    OAuthService configured;
    OAuthService unconfigured;

    @BeforeEach
    void setUp() {
        configured = new OAuthService(restTemplate, "google-id", "google-secret",
            "linkedin-id", "linkedin-secret", "http://localhost:8081");
        unconfigured = new OAuthService(restTemplate, "", "", "", "", "http://localhost:8081");
    }

    @Test
    void reportsConfiguredOnlyWhenBothIdAndSecretAreSet() {
        assertTrue(configured.isGoogleConfigured());
        assertTrue(configured.isLinkedInConfigured());
        assertFalse(unconfigured.isGoogleConfigured());
        assertFalse(unconfigured.isLinkedInConfigured());
    }

    @Test
    void googleAuthorizeUrlIncludesClientIdRedirectUriAndState() {
        String url = configured.buildGoogleAuthorizeUrl("the-state");

        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth"));
        assertTrue(url.contains("client_id=google-id"));
        assertTrue(url.contains("state=the-state"));
        assertTrue(url.contains("redirect_uri=http://localhost:8081/api/auth/oauth/google/callback"));
    }

    @Test
    void linkedinAuthorizeUrlIncludesClientIdRedirectUriAndState() {
        String url = configured.buildLinkedInAuthorizeUrl("the-state");

        assertTrue(url.startsWith("https://www.linkedin.com/oauth/v2/authorization"));
        assertTrue(url.contains("client_id=linkedin-id"));
        assertTrue(url.contains("state=the-state"));
    }

    @Test
    void buildingAnAuthorizeUrlWithoutCredentialsFails() {
        assertThrows(OAuthException.class, () -> unconfigured.buildGoogleAuthorizeUrl("s"));
        assertThrows(OAuthException.class, () -> unconfigured.buildLinkedInAuthorizeUrl("s"));
    }

    @Test
    void exchangingACodeWithoutCredentialsFails() {
        assertThrows(OAuthException.class, () -> unconfigured.exchangeGoogleCode("code"));
        assertThrows(OAuthException.class, () -> unconfigured.exchangeLinkedInCode("code"));
    }

    @Test
    void exchangeGoogleCodeReturnsTheProviderProfile() {
        lenient().when(restTemplate.postForObject(eq("https://oauth2.googleapis.com/token"), any(), eq(Map.class)))
            .thenReturn(Map.of("access_token", "tok-123"));
        lenient().when(restTemplate.exchange(eq("https://www.googleapis.com/oauth2/v3/userinfo"),
                eq(HttpMethod.GET), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of(
                "sub", "g-user-1", "email", "person@example.com", "email_verified", true, "name", "Person One")));

        OAuthUserInfo info = configured.exchangeGoogleCode("auth-code");

        assertEquals("g-user-1", info.providerId());
        assertEquals("person@example.com", info.email());
        assertTrue(info.emailVerified());
        assertEquals("Person One", info.fullName());
    }

    @Test
    void exchangeLinkedInCodeReturnsTheProviderProfile() {
        lenient().when(restTemplate.postForObject(eq("https://www.linkedin.com/oauth/v2/accessToken"), any(), eq(Map.class)))
            .thenReturn(Map.of("access_token", "tok-456"));
        lenient().when(restTemplate.exchange(eq("https://api.linkedin.com/v2/userinfo"),
                eq(HttpMethod.GET), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of(
                "sub", "li-user-1", "email", "person@example.com", "email_verified", true, "name", "Person One")));

        OAuthUserInfo info = configured.exchangeLinkedInCode("auth-code");

        assertEquals("li-user-1", info.providerId());
        assertEquals("person@example.com", info.email());
    }

    @Test
    void missingAccessTokenInTheProviderResponseFails() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenReturn(Map.of("error", "invalid_grant"));

        assertThrows(OAuthException.class, () -> configured.exchangeGoogleCode("bad-code"));
    }

    @Test
    void aNetworkFailureDuringTokenExchangeIsWrappedInOAuthException() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenThrow(new RestClientException("connection refused"));

        assertThrows(OAuthException.class, () -> configured.exchangeGoogleCode("code"));
    }

    @Test
    void anEmptyProfileResponseFails() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenReturn(Map.of("access_token", "tok-123"));
        lenient().when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(null));

        assertThrows(OAuthException.class, () -> configured.exchangeGoogleCode("code"));
    }

    @Test
    void aNetworkFailureFetchingTheProfileIsWrappedInOAuthException() {
        lenient().when(restTemplate.postForObject(any(String.class), any(), eq(Map.class)))
            .thenReturn(Map.of("access_token", "tok-123"));
        lenient().when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(), eq(Map.class)))
            .thenThrow(new RestClientException("timeout"));

        assertThrows(OAuthException.class, () -> configured.exchangeGoogleCode("code"));
    }
}
