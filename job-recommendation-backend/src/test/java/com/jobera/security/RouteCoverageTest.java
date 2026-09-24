package com.jobera.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.AptitudeTestRepository;
import com.jobera.repository.DreamRoleTestAssignmentRepository;
import com.jobera.repository.DreamRoleTestRepository;
import com.jobera.repository.ExternalCandidateRepository;
import com.jobera.repository.ExternalJobPostingRepository;
import com.jobera.repository.JobPostingRepository;
import com.jobera.repository.JobRatingRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.ResumeRepository;
import com.jobera.repository.TestAssignmentRepository;

/** Guards against shipping an endpoint that no access rule covers (unknown routes are denied at runtime). */
class RouteCoverageTest {

    /** Routes handled by the interceptor itself (public or shared-secret) instead of the access policy. */
    private static final Set<String> HANDLED_BY_INTERCEPTOR = Set.of(
        "/", "/api", "/api/health", "/api/auth/login", "/api/auth/register", "/api/auth/logout",
        "/api/auth/forgot-password", "/api/auth/reset-password", "/api/auth/login/2fa",
        "/api/auth/oauth/google", "/api/auth/oauth/google/callback",
        "/api/auth/oauth/linkedin", "/api/auth/oauth/linkedin/callback",
        "/api/webhooks/stripe");

    private AccessPolicy policy() {
        return new AccessPolicy(mock(RecruiterRepository.class), mock(JobPostingRepository.class),
            mock(ExternalJobPostingRepository.class), mock(ExternalCandidateRepository.class),
            mock(AptitudeTestRepository.class), mock(DreamRoleTestRepository.class), mock(ApplicationRepository.class),
            mock(TestAssignmentRepository.class), mock(DreamRoleTestAssignmentRepository.class),
            mock(JobRatingRepository.class), mock(ResumeRepository.class));
    }

    private List<String[]> allRoutes() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String[]> routes = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents("com.jobera")) {
            Class<?> controller = Class.forName(definition.getBeanClassName());
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String base = classMapping != null && classMapping.path().length > 0 ? classMapping.path()[0] : "";

            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String suffix = mapping.path().length > 0 ? mapping.path()[0] : "";
                String path = (base + suffix).replaceAll("\\{fileName\\}", "credentials_job_1_1.csv").replaceAll("\\{[^}]+\\}", "1");
                String verb = mapping.method().length > 0 ? mapping.method()[0].name() : "GET";
                routes.add(new String[] {verb, path, controller.getSimpleName() + "." + method.getName()});
            }
        }
        return routes;
    }

    @Test
    void everyControllerRouteIsCoveredByAnAccessRule() throws Exception {
        AccessPolicy policy = policy();
        List<String> uncovered = new ArrayList<>();

        List<String[]> routes = allRoutes();
        assertTrue(routes.size() > 100, "route scan should find the whole API, found " + routes.size());

        for (String[] route : routes) {
            if (HANDLED_BY_INTERCEPTOR.contains(route[1])) {
                continue;
            }
            if (!policy.hasRule(route[0], route[1])) {
                uncovered.add(route[0] + " " + route[1] + " (" + route[2] + ")");
            }
        }

        assertTrue(uncovered.isEmpty(), "Endpoints without an access rule:\n" + String.join("\n", uncovered));
    }

    @Test
    void debugEndpointsAreGone() throws Exception {
        for (String[] route : allRoutes()) {
            assertFalse(route[1].contains("debug"), "debug endpoint still present: " + route[1]);
            assertFalse(route[1].equals("/api/test"), "test endpoint still present");
            assertFalse(route[1].contains("check-uploads"), "upload listing endpoint still present");
        }
    }
}
