package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.entity.AuthToken;
import com.jobera.service.AccountDeletionService;
import com.jobera.service.AuthenticationService;
import com.jobera.service.DataExportService;
import com.jobera.util.TokenHasher;

import jakarta.servlet.http.HttpServletRequest;

/** Only the session-management endpoints added to AccountController - data export and account
 *  deletion have no existing test coverage to extend, and this is scoped to what was added. */
@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock DataExportService dataExportService;
    @Mock AccountDeletionService accountDeletionService;
    @Mock AuthenticationService authenticationService;
    @Mock com.jobera.service.CandidateProfileViewService candidateProfileViewService;

    AccountController controller;

    @BeforeEach
    void setUp() {
        controller = new AccountController(dataExportService, accountDeletionService, authenticationService,
            candidateProfileViewService);
    }

    private AuthToken sessionFor(Long id, String tokenHash, String userAgent, String ip) {
        AuthToken token = new AuthToken();
        token.setId(id);
        token.setToken(tokenHash);
        token.setUserAgent(userAgent);
        token.setIpAddress(ip);
        token.setCreatedAt(LocalDateTime.now().minusDays(1));
        token.setLastUsedAt(LocalDateTime.now());
        return token;
    }

    private HttpServletRequest requestWithAuth(String rawToken) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(rawToken == null ? null : "Bearer " + rawToken);
        return request;
    }

    @Test
    void listsSessionsAndFlagsTheCurrentOne() {
        String rawToken = "raw-token-abc";
        String currentHash = TokenHasher.hash(rawToken);
        AuthToken thisDevice = sessionFor(1L, currentHash,
            "Mozilla/5.0 (Windows NT 10.0) Chrome/120.0 Safari/537.36", "127.0.0.1");
        AuthToken otherDevice = sessionFor(2L, "some-other-hash",
            "Mozilla/5.0 (iPhone) Safari/604.1", "10.0.0.5");
        when(authenticationService.listSessions(10L)).thenReturn(List.of(thisDevice, otherDevice));

        ResponseEntity<List<Map<String, Object>>> response = controller.listSessions(10L, requestWithAuth(rawToken));

        List<Map<String, Object>> body = response.getBody();
        assertEquals(2, body.size());
        assertEquals("Chrome on Windows", body.get(0).get("device"));
        assertTrue((Boolean) body.get(0).get("current"));
        assertEquals("Safari on iOS", body.get(1).get("device"));
        assertFalse((Boolean) body.get(1).get("current"));
    }

    @Test
    void anUnknownUserAgentIsLabeledClearly() {
        AuthToken token = sessionFor(1L, "h", null, "127.0.0.1");
        when(authenticationService.listSessions(10L)).thenReturn(List.of(token));

        List<Map<String, Object>> body = controller.listSessions(10L, requestWithAuth(null)).getBody();

        assertEquals("Unknown device", body.get(0).get("device"));
        assertFalse((Boolean) body.get(0).get("current"));
    }

    @Test
    void profileViewsReturnsTheCountAndList() {
        when(candidateProfileViewService.getViewCount(10L)).thenReturn(3L);
        when(candidateProfileViewService.getViewsForCandidate(10L))
            .thenReturn(List.of(Map.of("companyName", "Acme", "viewedAt", LocalDateTime.now())));

        ResponseEntity<Map<String, Object>> response = controller.profileViews(10L);

        assertEquals(3L, response.getBody().get("count"));
        assertEquals(1, ((List<?>) response.getBody().get("views")).size());
    }

    @Test
    void revokeSessionReturnsOk() {
        ResponseEntity<Map<String, String>> response = controller.revokeSession(10L, 2L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void revokeSessionReturns404ForAnUnownedOrMissingSession() {
        doThrow(new AuthenticationService.SessionNotFoundException())
            .when(authenticationService).revokeSession(10L, 999L);

        ResponseEntity<Map<String, String>> response = controller.revokeSession(10L, 999L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
