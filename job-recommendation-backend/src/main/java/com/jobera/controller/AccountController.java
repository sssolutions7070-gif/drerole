package com.jobera.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.entity.AuthToken;
import com.jobera.service.AccountDeletionService;
import com.jobera.service.AuthenticationService;
import com.jobera.service.CandidateProfileViewService;
import com.jobera.service.DataExportService;
import com.jobera.util.TokenHasher;

import jakarta.servlet.http.HttpServletRequest;

/** Self-service data export, account deletion, active-session management, and (for a candidate)
 *  seeing who viewed their profile. All routes fall under the existing "/api/users/{userId}/**"
 *  self-ownership rule in AccessPolicy - no new rule needed. */
@RestController
@RequestMapping("/api/users/{userId}")
public class AccountController {

    private static final String ERROR_KEY = "error";

    private final DataExportService dataExportService;
    private final AccountDeletionService accountDeletionService;
    private final AuthenticationService authenticationService;
    private final CandidateProfileViewService candidateProfileViewService;

    public AccountController(DataExportService dataExportService, AccountDeletionService accountDeletionService,
                              AuthenticationService authenticationService,
                              CandidateProfileViewService candidateProfileViewService) {
        this.dataExportService = dataExportService;
        this.accountDeletionService = accountDeletionService;
        this.authenticationService = authenticationService;
        this.candidateProfileViewService = candidateProfileViewService;
    }

    @GetMapping("/data-export")
    public ResponseEntity<?> exportMyData(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(dataExportService.exportMyData(userId));
        } catch (DataExportService.UserNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/account")
    public ResponseEntity<Map<String, String>> deleteMyAccount(@PathVariable Long userId,
                                                                 @RequestBody Map<String, String> payload) {
        try {
            accountDeletionService.deleteMyAccount(userId, payload.get("password"));
            return ResponseEntity.ok(Map.of("message", "Account deleted"));
        } catch (AccountDeletionService.CannotDeleteAccountException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<Map<String, Object>>> listSessions(@PathVariable Long userId, HttpServletRequest request) {
        String currentTokenHash = currentTokenHash(request);
        List<Map<String, Object>> sessions = authenticationService.listSessions(userId).stream()
            .map(token -> toSessionView(token, currentTokenHash))
            .toList();
        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, String>> revokeSession(@PathVariable Long userId, @PathVariable Long sessionId) {
        try {
            authenticationService.revokeSession(userId, sessionId);
            return ResponseEntity.ok(Map.of("message", "Session revoked"));
        } catch (AuthenticationService.SessionNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/profile-views")
    public ResponseEntity<Map<String, Object>> profileViews(@PathVariable Long userId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("count", candidateProfileViewService.getViewCount(userId));
        response.put("views", candidateProfileViewService.getViewsForCandidate(userId));
        return ResponseEntity.ok(response);
    }

    private String currentTokenHash(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return TokenHasher.hash(header.substring(7));
        }
        return null;
    }

    private Map<String, Object> toSessionView(AuthToken token, String currentTokenHash) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", token.getId());
        view.put("device", describeDevice(token.getUserAgent()));
        view.put("ipAddress", token.getIpAddress());
        view.put("createdAt", token.getCreatedAt());
        view.put("lastUsedAt", token.getLastUsedAt());
        view.put("current", currentTokenHash != null && currentTokenHash.equals(token.getToken()));
        return view;
    }

    /** A deliberately simple, best-effort label from the User-Agent header - not a full parser,
     *  just enough for a human to recognize "that's my laptop" vs "that's not me". */
    private String describeDevice(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        String ua = userAgent.toLowerCase();
        String browser = ua.contains("edg/") ? "Edge"
            : ua.contains("chrome/") ? "Chrome"
            : ua.contains("firefox/") ? "Firefox"
            : ua.contains("safari/") ? "Safari"
            : "a browser";
        String os = ua.contains("windows") ? "Windows"
            : ua.contains("mac os") ? "macOS"
            : ua.contains("android") ? "Android"
            : (ua.contains("iphone") || ua.contains("ipad")) ? "iOS"
            : ua.contains("linux") ? "Linux"
            : "an unknown OS";
        return browser + " on " + os;
    }
}
