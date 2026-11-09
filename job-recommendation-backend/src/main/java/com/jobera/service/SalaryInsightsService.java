package com.jobera.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.jobera.entity.JobPosting;
import com.jobera.repository.JobPostingRepository;

/**
 * Turns the free-text salary ranges recruiters type on a job posting ("$80,000 - $120,000",
 * "90k-110k") into a searchable benchmark: what's actually being offered for a role, in a
 * location, at an experience level. Deliberately scoped to posted ranges rather than accepted
 * offers - those ranges are already public on the job listing itself, whereas an individual
 * candidate's negotiated offer is personal and, in a small sample, could be traced back to them.
 */
@Service
public class SalaryInsightsService {

    // Matches an optional currency symbol, digits with optional thousands separators, and an
    // optional k/K suffix meaning "thousand" - e.g. "$80,000", "90k", "120000".
    private static final Pattern NUMBER_PATTERN =
        Pattern.compile("\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kK])?");

    private final JobPostingRepository jobPostingRepository;
    private final AiInterviewClient aiInterviewClient;

    public SalaryInsightsService(JobPostingRepository jobPostingRepository, AiInterviewClient aiInterviewClient) {
        this.jobPostingRepository = jobPostingRepository;
        this.aiInterviewClient = aiInterviewClient;
    }

    public record SalaryRange(double min, double max) {
        double midpoint() {
            return (min + max) / 2.0;
        }
    }

    /**
     * Pulls the first one or two numbers out of a free-text salary range. A single number is
     * treated as both min and max. Text with no parseable number ("Competitive", "Negotiable")
     * returns empty rather than guessing.
     */
    public static Optional<SalaryRange> parseRange(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        List<Double> numbers = new ArrayList<>();
        Matcher matcher = NUMBER_PATTERN.matcher(text);
        while (matcher.find() && numbers.size() < 2) {
            String digits = matcher.group(1);
            if (digits == null) {
                continue;
            }
            double value = Double.parseDouble(digits.replace(",", ""));
            if (matcher.group(2) != null) {
                value *= 1000;
            }
            numbers.add(value);
        }
        if (numbers.isEmpty()) {
            return Optional.empty();
        }
        if (numbers.size() == 1) {
            return Optional.of(new SalaryRange(numbers.get(0), numbers.get(0)));
        }
        double first = numbers.get(0);
        double second = numbers.get(1);
        return Optional.of(new SalaryRange(Math.min(first, second), Math.max(first, second)));
    }

    /**
     * Aggregates approved, active postings matching the given (all optional, case-insensitive
     * "contains") filters into count/min/max/average/median statistics, plus a breakdown by
     * experience level so a candidate can see how a senior range compares to an entry-level one
     * for the same kind of role.
     */
    public Map<String, Object> getInsights(String title, String location, String experienceLevel) {
        List<JobPosting> candidates = jobPostingRepository
            .findByIsActiveTrueAndModerationStatusOrderByPostedDateDesc(JobPosting.MODERATION_APPROVED);

        List<SalaryRange> matched = new ArrayList<>();
        Map<String, List<SalaryRange>> byExperienceLevel = new LinkedHashMap<>();

        for (JobPosting job : candidates) {
            if (!matchesFilter(job.getTitle(), title)) continue;
            if (!matchesFilter(job.getLocation(), location)) continue;
            if (experienceLevel != null && !experienceLevel.isBlank()
                && !experienceLevel.equalsIgnoreCase(job.getExperienceLevel())) {
                continue;
            }
            Optional<SalaryRange> range = parseRange(job.getSalaryRange());
            if (range.isEmpty()) continue;

            matched.add(range.get());
            String level = job.getExperienceLevel() != null && !job.getExperienceLevel().isBlank()
                ? job.getExperienceLevel() : "Unspecified";
            byExperienceLevel.computeIfAbsent(level, k -> new ArrayList<>()).add(range.get());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchingPostings", matched.size());
        result.put("overall", summarize(matched));

        Map<String, Object> byLevel = new LinkedHashMap<>();
        byExperienceLevel.forEach((level, ranges) -> byLevel.put(level, summarize(ranges)));
        result.put("byExperienceLevel", byLevel);

        return result;
    }

    /** Reuses getInsights()'s own market aggregation as the factual grounding for an AI-written
     *  negotiation talking-points/counter-offer suggestion (AiInterviewClient.
     *  generateNegotiationAdvice) - the model turns real posted-range data plus the candidate's
     *  own offer into concrete advice, rather than inventing salary numbers itself. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getNegotiationAdvice(String jobTitle, String location, String experienceLevel,
                                                      String offeredSalaryText) {
        Map<String, Object> insights = getInsights(jobTitle, location, experienceLevel);
        Map<String, Object> overall = (Map<String, Object>) insights.get("overall");

        Double marketMin = toDouble(overall.get("minSalary"));
        Double marketMedian = toDouble(overall.get("medianSalary"));
        Double marketMax = toDouble(overall.get("maxSalary"));
        int sampleSize = (int) overall.get("count");

        Double offeredSalary = parseRange(offeredSalaryText).map(SalaryRange::midpoint).orElse(null);

        Map<String, Object> advice = aiInterviewClient.generateNegotiationAdvice(
            jobTitle, offeredSalary, marketMin, marketMedian, marketMax, sampleSize);

        Map<String, Object> result = new LinkedHashMap<>(advice);
        result.put("offeredSalary", offeredSalary);
        result.put("marketData", overall);
        return result;
    }

    private Double toDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    private boolean matchesFilter(String fieldValue, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return fieldValue != null && fieldValue.toLowerCase().contains(filter.toLowerCase());
    }

    private Map<String, Object> summarize(List<SalaryRange> ranges) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (ranges.isEmpty()) {
            summary.put("count", 0);
            summary.put("minSalary", null);
            summary.put("maxSalary", null);
            summary.put("averageSalary", null);
            summary.put("medianSalary", null);
            return summary;
        }

        double min = ranges.stream().mapToDouble(SalaryRange::min).min().orElse(0);
        double max = ranges.stream().mapToDouble(SalaryRange::max).max().orElse(0);
        double average = ranges.stream().mapToDouble(SalaryRange::midpoint).average().orElse(0);

        List<Double> midpoints = ranges.stream().map(SalaryRange::midpoint)
            .sorted(Comparator.naturalOrder()).toList();
        double median = midpoints.size() % 2 == 1
            ? midpoints.get(midpoints.size() / 2)
            : (midpoints.get(midpoints.size() / 2 - 1) + midpoints.get(midpoints.size() / 2)) / 2.0;

        summary.put("count", ranges.size());
        summary.put("minSalary", Math.round(min));
        summary.put("maxSalary", Math.round(max));
        summary.put("averageSalary", Math.round(average));
        summary.put("medianSalary", Math.round(median));
        return summary;
    }
}
