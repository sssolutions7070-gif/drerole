package com.jobera.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.jobera.entity.Application;
import com.jobera.repository.ApplicationRepository;
import com.jobera.repository.ProfileViewRepository;

/**
 * "My Insights" for a candidate - recruiters have had a per-job analytics breakdown and admins a
 * platform-wide one for a while; candidates had no analytics view of their own at all. This rolls
 * up a candidate's own applications (status breakdown, average match score, and which skills
 * come up missing most often across the jobs they've applied to) plus how many recruiters have
 * viewed their profile.
 */
@Service
public class CandidateAnalyticsService {

    private static final int TOP_MISSING_SKILLS = 5;

    private final ApplicationRepository applicationRepository;
    private final ProfileViewRepository profileViewRepository;

    public CandidateAnalyticsService(ApplicationRepository applicationRepository,
                                      ProfileViewRepository profileViewRepository) {
        this.applicationRepository = applicationRepository;
        this.profileViewRepository = profileViewRepository;
    }

    public Map<String, Object> getInsights(Long userId) {
        List<Application> applications = applicationRepository.findByUserId(userId);

        Map<String, Object> insights = new LinkedHashMap<>();
        insights.put("totalApplications", applications.size());
        insights.put("byStatus", byStatus(applications));
        insights.put("averageMatchPercentage", averageMatchPercentage(applications));
        insights.put("topMissingSkills", SkillGapAnalyzer.topMissingSkills(applications, TOP_MISSING_SKILLS));
        insights.put("profileViewCount", profileViewRepository.countByViewedUserId(userId));
        return insights;
    }

    private Map<String, Long> byStatus(List<Application> applications) {
        return applications.stream()
            .collect(Collectors.groupingBy(
                a -> a.getStatus() == null ? "UNKNOWN" : a.getStatus(),
                LinkedHashMap::new, Collectors.counting()));
    }

    private Double averageMatchPercentage(List<Application> applications) {
        OptionalDouble average = applications.stream()
            .filter(a -> a.getMatchPercentage() != null)
            .mapToDouble(Application::getMatchPercentage)
            .average();
        return average.isPresent() ? Math.round(average.getAsDouble() * 10.0) / 10.0 : null;
    }
}
