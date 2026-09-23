package com.jobera.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.jobera.entity.User;
import com.jobera.security.OAuthStateStore;
import com.jobera.service.AuthenticationService;
import com.jobera.service.OAuthService;
import com.jobera.service.SocialLoginService;

/**
 * "Sign in with Google" / "Sign in with LinkedIn". Every route here is a full-page browser
 * redirect, not a fetch/XHR call - the browser navigates to the provider and back, so there's no
 * JSON response to return; success and failure both end in a redirect to the frontend, which
 * reads a token (or error) off the URL. All four routes are public
 * (AuthInterceptor.PUBLIC_PATHS) - there's no user to authenticate yet at any point in this flow.
 */
@RestController
@RequestMapping("/api/auth/oauth")
public class OAuthController {

    private final OAuthService oAuthService;
    private final SocialLoginService socialLoginService;
    private final AuthenticationService authenticationService;
    private final OAuthStateStore stateStore;
    private final String frontendUrl;

    public OAuthController(OAuthService oAuthService,
                            SocialLoginService socialLoginService,
                            AuthenticationService authenticationService,
                            OAuthStateStore stateStore,
                            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.oAuthService = oAuthService;
        this.socialLoginService = socialLoginService;
        this.authenticationService = authenticationService;
        this.stateStore = stateStore;
        this.frontendUrl = frontendUrl;
    }

    @GetMapping("/google")
    public ResponseEntity<?> startGoogleLogin() {
        return redirectOrError(() -> oAuthService.buildGoogleAuthorizeUrl(stateStore.issue()));
    }

    @GetMapping("/linkedin")
    public ResponseEntity<?> startLinkedInLogin() {
        return redirectOrError(() -> oAuthService.buildLinkedInAuthorizeUrl(stateStore.issue()));
    }

    @GetMapping("/google/callback")
    public ResponseEntity<?> googleCallback(@RequestParam(required = false) String code,
                                             @RequestParam(required = false) String state,
                                             @RequestParam(required = false) String error) {
        return handleCallback("google", code, state, error,
            () -> oAuthService.exchangeGoogleCode(code));
    }

    @GetMapping("/linkedin/callback")
    public ResponseEntity<?> linkedinCallback(@RequestParam(required = false) String code,
                                               @RequestParam(required = false) String state,
                                               @RequestParam(required = false) String error) {
        return handleCallback("linkedin", code, state, error,
            () -> oAuthService.exchangeLinkedInCode(code));
    }

    private ResponseEntity<?> redirectOrError(java.util.function.Supplier<String> urlSupplier) {
        try {
            return redirect(urlSupplier.get());
        } catch (OAuthService.OAuthException e) {
            return redirect(loginErrorUrl(e.getMessage()));
        }
    }

    private ResponseEntity<?> handleCallback(String provider, String code, String state, String providerError,
                                              java.util.function.Supplier<OAuthService.OAuthUserInfo> exchange) {
        if (providerError != null) {
            return redirect(loginErrorUrl("Sign-in with " + provider + " was cancelled or failed"));
        }
        if (!stateStore.consume(state)) {
            return redirect(loginErrorUrl("This sign-in link has expired - please try again"));
        }
        if (code == null || code.isBlank()) {
            return redirect(loginErrorUrl("Missing authorization code from " + provider));
        }
        try {
            OAuthService.OAuthUserInfo info = exchange.get();
            User user = socialLoginService.findOrCreateUser(provider, info);
            String token = authenticationService.generateAuthToken(user);
            return redirect(UriComponentsBuilder.fromUriString(frontendUrl + "/oauth-callback")
                .queryParam("token", token)
                .build().encode().toUriString());
        } catch (OAuthService.OAuthException e) {
            return redirect(loginErrorUrl(e.getMessage()));
        }
    }

    private String loginErrorUrl(String message) {
        return UriComponentsBuilder.fromUriString(frontendUrl + "/login")
            .queryParam("oauthError", message)
            .build().encode().toUriString();
    }

    private ResponseEntity<?> redirect(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, url);
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}
