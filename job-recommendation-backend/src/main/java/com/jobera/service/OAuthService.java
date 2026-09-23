package com.jobera.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * "Sign in with Google" / "Sign in with LinkedIn" via the standard OAuth2 authorization-code
 * flow, called directly with RestTemplate rather than Spring Security's OAuth2 client - this
 * app doesn't use Spring Security (see application.properties, it's explicitly excluded) and
 * has its own AuthInterceptor/AccessPolicy instead, so adding a second, parallel security
 * framework just for this would be a bigger, riskier change than calling two well-documented
 * REST endpoints directly.
 *
 * Needs real credentials to do anything beyond building the authorize URL - see
 * secrets.properties.example for GOOGLE_OAUTH_CLIENT_ID etc. Every method here checks
 * isConfigured() first and fails with a clear message rather than a raw HTTP error when they're
 * not set, the same pattern TwoFactorService uses for its own required key.
 */
@Service
public class OAuthService {

    public static class OAuthException extends RuntimeException {
        public OAuthException(String message) {
            super(message);
        }
    }

    public record OAuthUserInfo(String providerId, String email, boolean emailVerified, String fullName) {}

    private final String googleClientId;
    private final String googleClientSecret;
    private final String linkedinClientId;
    private final String linkedinClientSecret;
    private final String backendBaseUrl;
    private final RestTemplate restTemplate;

    public OAuthService(RestTemplate restTemplate,
                         @Value("${app.oauth.google-client-id:}") String googleClientId,
                         @Value("${app.oauth.google-client-secret:}") String googleClientSecret,
                         @Value("${app.oauth.linkedin-client-id:}") String linkedinClientId,
                         @Value("${app.oauth.linkedin-client-secret:}") String linkedinClientSecret,
                         @Value("${app.backend-base-url:http://localhost:8081}") String backendBaseUrl) {
        this.restTemplate = restTemplate;
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.linkedinClientId = linkedinClientId;
        this.linkedinClientSecret = linkedinClientSecret;
        this.backendBaseUrl = backendBaseUrl;
    }

    public boolean isGoogleConfigured() {
        return !googleClientId.isBlank() && !googleClientSecret.isBlank();
    }

    public boolean isLinkedInConfigured() {
        return !linkedinClientId.isBlank() && !linkedinClientSecret.isBlank();
    }

    private String googleRedirectUri() {
        return backendBaseUrl + "/api/auth/oauth/google/callback";
    }

    private String linkedinRedirectUri() {
        return backendBaseUrl + "/api/auth/oauth/linkedin/callback";
    }

    public String buildGoogleAuthorizeUrl(String state) {
        if (!isGoogleConfigured()) {
            throw new OAuthException("Google sign-in is not configured on this server");
        }
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
            .queryParam("client_id", googleClientId)
            .queryParam("redirect_uri", googleRedirectUri())
            .queryParam("response_type", "code")
            .queryParam("scope", "openid email profile")
            .queryParam("state", state)
            .build().encode().toUriString();
    }

    public String buildLinkedInAuthorizeUrl(String state) {
        if (!isLinkedInConfigured()) {
            throw new OAuthException("LinkedIn sign-in is not configured on this server");
        }
        return UriComponentsBuilder.fromUriString("https://www.linkedin.com/oauth/v2/authorization")
            .queryParam("client_id", linkedinClientId)
            .queryParam("redirect_uri", linkedinRedirectUri())
            .queryParam("response_type", "code")
            .queryParam("scope", "openid profile email")
            .queryParam("state", state)
            .build().encode().toUriString();
    }

    public OAuthUserInfo exchangeGoogleCode(String code) {
        if (!isGoogleConfigured()) {
            throw new OAuthException("Google sign-in is not configured on this server");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", googleClientId);
        form.add("client_secret", googleClientSecret);
        form.add("redirect_uri", googleRedirectUri());
        form.add("grant_type", "authorization_code");

        String accessToken = fetchAccessToken("https://oauth2.googleapis.com/token", form);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        Map<String, Object> userInfo = getJson("https://www.googleapis.com/oauth2/v3/userinfo", headers);

        return new OAuthUserInfo(
            asString(userInfo.get("sub")),
            asString(userInfo.get("email")),
            Boolean.TRUE.equals(userInfo.get("email_verified")),
            asString(userInfo.get("name"))
        );
    }

    public OAuthUserInfo exchangeLinkedInCode(String code) {
        if (!isLinkedInConfigured()) {
            throw new OAuthException("LinkedIn sign-in is not configured on this server");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", linkedinClientId);
        form.add("client_secret", linkedinClientSecret);
        form.add("redirect_uri", linkedinRedirectUri());
        form.add("grant_type", "authorization_code");

        String accessToken = fetchAccessToken("https://www.linkedin.com/oauth/v2/accessToken", form);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        // LinkedIn's OpenID Connect userinfo endpoint ("Sign In with LinkedIn using OpenID Connect").
        Map<String, Object> userInfo = getJson("https://api.linkedin.com/v2/userinfo", headers);

        return new OAuthUserInfo(
            asString(userInfo.get("sub")),
            asString(userInfo.get("email")),
            Boolean.TRUE.equals(userInfo.get("email_verified")),
            asString(userInfo.get("name"))
        );
    }

    private String fetchAccessToken(String tokenUrl, MultiValueMap<String, String> form) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        Map<String, Object> response;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.postForObject(tokenUrl, new HttpEntity<>(form, headers), Map.class);
            response = body;
        } catch (RestClientException e) {
            throw new OAuthException("Failed to exchange the authorization code with the provider");
        }
        if (response == null || response.get("access_token") == null) {
            throw new OAuthException("Provider did not return an access token");
        }
        return asString(response.get("access_token"));
    }

    private Map<String, Object> getJson(String url, HttpHeaders headers) {
        try {
            @SuppressWarnings("unchecked")
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new OAuthException("Provider returned an empty profile response");
            }
            return body;
        } catch (RestClientException e) {
            throw new OAuthException("Failed to fetch the account profile from the provider");
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
