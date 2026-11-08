package com.jobera.config;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.jobera.entity.User;
import com.jobera.security.AccessPolicy;
import com.jobera.service.ApiKeyService;
import com.jobera.service.AuthenticationService;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    @Mock AuthenticationService authenticationService;
    @Mock AccessPolicy accessPolicy;
    @Mock ApiKeyService apiKeyService;

    AuthInterceptor interceptor;
    MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new AuthInterceptor(authenticationService, accessPolicy, apiKeyService, "");
        response = new MockHttpServletResponse();
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    private User user(long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    @Test
    void publicEndpointsNeedNoToken() throws Exception {
        for (String path : new String[] {"/api/health", "/api/auth/login", "/api/auth/register"}) {
            assertTrue(interceptor.preHandle(request("POST", path), new MockHttpServletResponse(), new Object()), path);
        }
        verifyNoInteractions(accessPolicy);
    }

    @Test
    void theGoogleCalendarOAuthCallbackNeedsNoTokenEitherSinceGoogleRedirectsTheBareBrowserThere() throws Exception {
        assertTrue(interceptor.preHandle(request("GET", "/api/calendar/google/callback"), new MockHttpServletResponse(), new Object()));
        verifyNoInteractions(accessPolicy);
    }

    @Test
    void preflightRequestsPass() throws Exception {
        assertTrue(interceptor.preHandle(request("OPTIONS", "/api/jobs"), response, new Object()));
    }

    @Test
    void missingTokenIsRejectedWith401() throws Exception {
        assertFalse(interceptor.preHandle(request("GET", "/api/jobs/recommendations/skills"), response, new Object()));
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Authentication required"));
    }

    @Test
    void invalidTokenIsRejectedWith401() throws Exception {
        when(authenticationService.validateToken("bad")).thenReturn(Optional.empty());
        MockHttpServletRequest request = request("GET", "/api/jobs/recommendations/skills");
        request.addHeader("Authorization", "Bearer bad");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void validTokenAllowedByThePolicyPassesAndExposesTheUser() throws Exception {
        User user = user(5);
        when(authenticationService.validateToken("good")).thenReturn(Optional.of(user));
        when(accessPolicy.isAllowed(eq(user), eq("GET"), eq("/api/jobs/recommendations/skills"), any())).thenReturn(true);
        MockHttpServletRequest request = request("GET", "/api/jobs/recommendations/skills");
        request.addHeader("Authorization", "Bearer good");

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertSame(user, request.getAttribute(AuthInterceptor.AUTHENTICATED_USER));
    }

    @Test
    void validApiKeyAllowedByThePolicyPassesAndExposesTheUser() throws Exception {
        User user = user(5);
        when(apiKeyService.authenticate("dr_live_good")).thenReturn(Optional.of(user));
        when(accessPolicy.isAllowed(eq(user), eq("GET"), eq("/api/jobs/recommendations/skills"), any())).thenReturn(true);
        MockHttpServletRequest request = request("GET", "/api/jobs/recommendations/skills");
        request.addHeader("X-API-Key", "dr_live_good");

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertSame(user, request.getAttribute(AuthInterceptor.AUTHENTICATED_USER));
    }

    @Test
    void invalidApiKeyIsRejectedWith401() throws Exception {
        when(apiKeyService.authenticate("bad")).thenReturn(Optional.empty());
        MockHttpServletRequest request = request("GET", "/api/jobs/recommendations/skills");
        request.addHeader("X-API-Key", "bad");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void bearerTokenTakesPrecedenceOverAnApiKeyIfBothArePresent() throws Exception {
        User user = user(5);
        when(authenticationService.validateToken("good")).thenReturn(Optional.of(user));
        when(accessPolicy.isAllowed(eq(user), eq("GET"), eq("/api/jobs/recommendations/skills"), any())).thenReturn(true);
        MockHttpServletRequest request = request("GET", "/api/jobs/recommendations/skills");
        request.addHeader("Authorization", "Bearer good");
        request.addHeader("X-API-Key", "should-be-ignored");

        assertTrue(interceptor.preHandle(request, response, new Object()));
        verifyNoInteractions(apiKeyService);
    }

    @Test
    void jobBrowsingNeedsNoTokenButPersonalizedRoutesStillDo() throws Exception {
        for (String path : new String[] {
                "/api/jobs", "/api/jobs/search", "/api/jobs/42", "/api/jobs/42/ratings", "/api/jobs/42/rating/stats"}) {
            assertTrue(interceptor.preHandle(request("GET", path), new MockHttpServletResponse(), new Object()), path);
        }
        verifyNoInteractions(accessPolicy);

        // Recommendations are personalized to a resume on file, so they stay behind a login,
        // even though the path shape looks similar to the now-public job-browsing routes.
        for (String path : new String[] {
                "/api/jobs/recommendations/skills", "/api/jobs/recommendations/42", "/api/jobs/recommendations/42/detailed"}) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            assertFalse(interceptor.preHandle(request("GET", path), resp, new Object()), path);
            assertEquals(401, resp.getStatus(), path);
        }
    }

    @Test
    void jobBrowsingPublicAccessIsGetOnly() throws Exception {
        // A public *read* doesn't mean a public *write* - posting/rating/applying still needs a
        // real account, even though it shares a URL prefix with the now-public browsing routes.
        assertFalse(interceptor.preHandle(request("POST", "/api/jobs/42/rate/5"), response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void validTokenDeniedByThePolicyIsRejectedWith403() throws Exception {
        User user = user(5);
        when(authenticationService.validateToken("good")).thenReturn(Optional.of(user));
        when(accessPolicy.isAllowed(eq(user), eq("GET"), eq("/api/admin/users"), any())).thenReturn(false);
        MockHttpServletRequest request = request("GET", "/api/admin/users");
        request.addHeader("Authorization", "Bearer good");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(403, response.getStatus());
        assertEquals(null, request.getAttribute(AuthInterceptor.AUTHENTICATED_USER));
    }

    @Test
    void queryParametersAreDecodedAndPassedToThePolicy() {
        MockHttpServletRequest request = request("GET", "/api/x");
        request.setQueryString("recruiterId=7&name=a%20b&tag=1&tag=2");

        Map<String, String[]> params = AuthInterceptor.queryParams(request);

        assertArrayEquals(new String[] {"7"}, params.get("recruiterId"));
        assertArrayEquals(new String[] {"a b"}, params.get("name"));
        assertArrayEquals(new String[] {"1", "2"}, params.get("tag"));
    }

    @Test
    void proctoringCallbackIsTrustedFromLoopbackWhenNoKeyConfigured() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/aptitude-tests/12/violations");
        request.setRemoteAddr("127.0.0.1");
        assertTrue(interceptor.preHandle(request, response, new Object()));

        MockHttpServletRequest remote = request("POST", "/api/aptitude-tests/12/violations");
        remote.setRemoteAddr("10.0.0.5");
        assertFalse(interceptor.preHandle(remote, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void proctoringCallbackRequiresTheSharedKeyWhenConfigured() throws Exception {
        AuthInterceptor keyed = new AuthInterceptor(authenticationService, accessPolicy, apiKeyService, "s3cr3t");

        MockHttpServletRequest good = request("POST", "/api/aptitude-tests/12/violations");
        good.setRemoteAddr("10.0.0.5");
        good.addHeader(AuthInterceptor.PROCTORING_KEY_HEADER, "s3cr3t");
        assertTrue(keyed.preHandle(good, response, new Object()));

        MockHttpServletRequest bad = request("POST", "/api/aptitude-tests/12/violations");
        bad.setRemoteAddr("127.0.0.1");
        bad.addHeader(AuthInterceptor.PROCTORING_KEY_HEADER, "wrong");
        assertFalse(keyed.preHandle(bad, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void proctoringKeyDoesNotOpenOtherEndpoints() throws Exception {
        AuthInterceptor keyed = new AuthInterceptor(authenticationService, accessPolicy, apiKeyService, "s3cr3t");
        MockHttpServletRequest request = request("POST", "/api/aptitude-tests/submit");
        request.addHeader(AuthInterceptor.PROCTORING_KEY_HEADER, "s3cr3t");

        assertFalse(keyed.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }
}
