package com.jobera.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;

import com.jobera.entity.Application;
import com.jobera.entity.JobPosting;
import com.jobera.entity.User;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.JobPostingRepository;

/**
 * Aggregate, anonymous diversity breakdown of everyone who has ever applied to a recruiter's
 * jobs - built entirely from User's optional, voluntary self-identification fields
 * (genderIdentity, ethnicity, veteranStatus, disabilityStatus; see User entity). This never
 * exposes any individual candidate's answer, only rolled-up counts/percentages per category, and
 * a category is withheld entirely below MIN_RESPONSES_TO_REPORT self-identified responses so a
 * small applicant pool can't be reverse-engineered into identifying one person.
 */
@Service
public class DiversityAnalyticsService {

    private static final long MIN_RESPONSES_TO_REPORT = 5;

    private final JobPostingRepository jobPostingRepository;
    private final ApplicationRepository applicationRepository;

    public DiversityAnalyticsService(JobPostingRepository jobPostingRepository,
                                      ApplicationRepository applicationRepository) {
        this.jobPostingRepository = jobPostingRepository;
        this.applicationRepository = applicationRepository;
    }

    public Map<String, Object> getAggregateForRecruiter(Long recruiterId) {
        List<JobPosting> jobs = jobPostingRepository.findByRecruiterId(recruiterId);

        List<User> candidates = jobs.stream()
            .flatMap(job -> applicationRepository.findByJobPostingId(job.getId()).stream())
            .map(Application::getUser)
            .filter(user -> user != null)
            .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalApplicants", candidates.size());
        result.put("genderIdentity", breakdownFor(candidates, User::getGenderIdentity));
        result.put("ethnicity", breakdownFor(candidates, User::getEthnicity));
        result.put("veteranStatus", breakdownFor(candidates, User::getVeteranStatus));
        result.put("disabilityStatus", breakdownFor(candidates, User::getDisabilityStatus));
        return result;
    }

    private Map<String, Object> breakdownFor(List<User> candidates, java.util.function.Function<User, String> field) {
        Map<String, Long> counts = new TreeMap<>();
        long answered = 0;
        for (User candidate : candidates) {
            String value = field.apply(candidate);
            if (value == null || value.isBlank()) {
                continue;
            }
            answered++;
            counts.merge(value, 1L, Long::sum);
        }

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("responseCount", answered);

        if (answered < MIN_RESPONSES_TO_REPORT) {
            breakdown.put("reportable", false);
            breakdown.put("percentages", Map.of());
            return breakdown;
        }

        Map<String, Double> percentages = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            percentages.put(entry.getKey(), round1(entry.getValue() * 100.0 / answered));
        }
        breakdown.put("reportable", true);
        breakdown.put("percentages", percentages);
        return breakdown;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
