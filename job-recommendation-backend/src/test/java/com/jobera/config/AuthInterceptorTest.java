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
import com.jobera.service.AuthenticationService;

@ExtendWith(MockitoExtension.class)
class AuthInterceptorTest {

    @Mock AuthenticationService authenticationService;
    @Mock AccessPolicy accessPolicy;

    AuthInterceptor interceptor;
    MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        interceptor = new AuthInterceptor(authenticationService, accessPolicy, "");
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
    void preflightRequestsPass() throws Exception {
        assertTrue(interceptor.preHandle(request("OPTIONS", "/api/jobs"), response, new Object()));
    }

    @Test
    void missingTokenIsRejectedWith401() throws Exception {
        assertFalse(interceptor.preHandle(request("GET", "/api/jobs"), response, new Object()));
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("Authentication required"));
    }

    @Test
    void invalidTokenIsRejectedWith401() throws Exception {
        when(authenticationService.validateToken("bad")).thenReturn(Optional.empty());
        MockHttpServletRequest request = request("GET", "/api/jobs");
        request.addHeader("Authorization", "Bearer bad");

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void validTokenAllowedByThePolicyPassesAndExposesTheUser() throws Exception {
        User user = user(5);
        when(authenticationService.validateToken("good")).thenReturn(Optional.of(user));
        when(accessPolicy.isAllowed(eq(user), eq("GET"), eq("/api/jobs"), any())).thenReturn(true);
        MockHttpServletRequest request = request("GET", "/api/jobs");
        request.addHeader("Authorization", "Bearer good");

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertSame(user, request.getAttribute(AuthInterceptor.AUTHENTICATED_USER));
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
        AuthInterceptor keyed = new AuthInterceptor(authenticationService, accessPolicy, "s3cr3t");

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
        AuthInterceptor keyed = new AuthInterceptor(authenticationService, accessPolicy, "s3cr3t");
        MockHttpServletRequest request = request("POST", "/api/aptitude-tests/submit");
        request.addHeader(AuthInterceptor.PROCTORING_KEY_HEADER, "s3cr3t");

        assertFalse(keyed.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }
}
