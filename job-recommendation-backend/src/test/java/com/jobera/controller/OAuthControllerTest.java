package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.entity.User;
import com.jobera.security.OAuthStateStore;
import com.jobera.service.AuthenticationService;
import com.jobera.service.OAuthService;
import com.jobera.service.OAuthService.OAuthException;
import com.jobera.service.OAuthService.OAuthUserInfo;
import com.jobera.service.SocialLoginService;

@ExtendWith(MockitoExtension.class)
class OAuthControllerTest {

    @Mock OAuthService oAuthService;
    @Mock SocialLoginService socialLoginService;
    @Mock AuthenticationService authenticationService;
    @Mock OAuthStateStore stateStore;

    OAuthController controller;

    @BeforeEach
    void setUp() {
        controller = new OAuthController(oAuthService, socialLoginService, authenticationService, stateStore,
            "http://localhost:3000");
    }

    private String locationOf(ResponseEntity<?> response) {
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        return response.getHeaders().getFirst(org.springframework.http.HttpHeaders.LOCATION);
    }

    @Test
    void startingGoogleLoginRedirectsToTheAuthorizeUrl() {
        when(stateStore.issue()).thenReturn("state-1");
        when(oAuthService.buildGoogleAuthorizeUrl("state-1")).thenReturn("https://accounts.google.com/authorize?state=state-1");

        String location = locationOf(controller.startGoogleLogin());

        assertEquals("https://accounts.google.com/authorize?state=state-1", location);
    }

    @Test
    void startingGoogleLoginWhenNotConfiguredRedirectsToLoginWithAnError() {
        when(stateStore.issue()).thenReturn("state-1");
        when(oAuthService.buildGoogleAuthorizeUrl("state-1")).thenThrow(new OAuthException("Google sign-in is not configured on this server"));

        String location = locationOf(controller.startGoogleLogin());

        assertTrue(location.startsWith("http://localhost:3000/login"));
        assertTrue(location.contains("oauthError"));
    }

    @Test
    void startingLinkedInLoginRedirectsToTheAuthorizeUrl() {
        when(stateStore.issue()).thenReturn("state-2");
        when(oAuthService.buildLinkedInAuthorizeUrl("state-2")).thenReturn("https://linkedin.com/authorize?state=state-2");

        String location = locationOf(controller.startLinkedInLogin());

        assertEquals("https://linkedin.com/authorize?state=state-2", location);
    }

    @Test
    void aSuccessfulGoogleCallbackRedirectsToTheFrontendWithAToken() {
        when(stateStore.consume("state-1")).thenReturn(true);
        OAuthUserInfo info = new OAuthUserInfo("g-1", "person@example.com", true, "Person One");
        when(oAuthService.exchangeGoogleCode("auth-code")).thenReturn(info);
        User user = new User("person@example.com", "hash", "Person One");
        when(socialLoginService.findOrCreateUser("google", info)).thenReturn(user);
        when(authenticationService.generateAuthToken(user)).thenReturn("session-token");

        String location = locationOf(controller.googleCallback("auth-code", "state-1", null));

        assertEquals("http://localhost:3000/oauth-callback?token=session-token", location);
    }

    @Test
    void aGoogleCallbackWithAProviderErrorRedirectsToLoginWithoutExchangingACode() {
        String location = locationOf(controller.googleCallback(null, "state-1", "access_denied"));

        assertTrue(location.startsWith("http://localhost:3000/login"));
        assertTrue(location.contains("oauthError"));
        verify(oAuthService, never()).exchangeGoogleCode(any());
    }

    @Test
    void aGoogleCallbackWithAnExpiredOrUnknownStateIsRejected() {
        when(stateStore.consume("bad-state")).thenReturn(false);

        String location = locationOf(controller.googleCallback("auth-code", "bad-state", null));

        assertTrue(location.startsWith("http://localhost:3000/login"));
        verify(oAuthService, never()).exchangeGoogleCode(any());
    }

    @Test
    void aGoogleCallbackWithoutACodeIsRejected() {
        when(stateStore.consume("state-1")).thenReturn(true);

        String location = locationOf(controller.googleCallback(null, "state-1", null));

        assertTrue(location.startsWith("http://localhost:3000/login"));
        verify(oAuthService, never()).exchangeGoogleCode(any());
    }

    @Test
    void aFailedExchangeDuringTheGoogleCallbackRedirectsToLoginWithTheErrorMessage() {
        when(stateStore.consume("state-1")).thenReturn(true);
        when(oAuthService.exchangeGoogleCode("auth-code")).thenThrow(new OAuthException("Your google email address is not verified"));

        String location = locationOf(controller.googleCallback("auth-code", "state-1", null));

        assertTrue(location.startsWith("http://localhost:3000/login"));
        assertTrue(location.contains("oauthError"));
    }

    @Test
    void aSuccessfulLinkedInCallbackRedirectsToTheFrontendWithAToken() {
        when(stateStore.consume("state-2")).thenReturn(true);
        OAuthUserInfo info = new OAuthUserInfo("li-1", "person@example.com", true, "Person One");
        when(oAuthService.exchangeLinkedInCode("auth-code")).thenReturn(info);
        User user = new User("person@example.com", "hash", "Person One");
        when(socialLoginService.findOrCreateUser("linkedin", info)).thenReturn(user);
        when(authenticationService.generateAuthToken(user)).thenReturn("session-token-2");

        String location = locationOf(controller.linkedinCallback("auth-code", "state-2", null));

        assertEquals("http://localhost:3000/oauth-callback?token=session-token-2", location);
    }
}
