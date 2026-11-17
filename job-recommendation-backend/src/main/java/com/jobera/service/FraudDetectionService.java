package com.jobera.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.jobera.entity.JobPosting;

/**
 * A heuristic scanner for common job-scam patterns (upfront-fee requests, unrealistic pay
 * promises, pyramid/MLM language, off-platform contact requests) - flags a job posting for a
 * human admin to look at more closely during moderation, never auto-rejects it on its own. False
 * positives are expected and acceptable here since a person always makes the actual call; the
 * point is surfacing the signal, not automating the decision.
 */
@Service
public class FraudDetectionService {

    public static final String RISK_LOW = "LOW";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_HIGH = "HIGH";

    private record Signal(Pattern pattern, int weight, String reason) {}

    private final List<Signal> signals = List.of(
        new Signal(Pattern.compile("(registration|processing|training|starter kit)\\s+fee", Pattern.CASE_INSENSITIVE),
            35, "Asks the candidate to pay a fee to proceed"),
        new Signal(Pattern.compile("(wire transfer|western union|moneygram|send (us )?money|deposit required)", Pattern.CASE_INSENSITIVE),
            35, "Requests a money transfer or deposit from the candidate"),
        new Signal(Pattern.compile("(bitcoin|crypto\\s*wallet|usdt|ethereum)", Pattern.CASE_INSENSITIVE),
            25, "Mentions cryptocurrency payment, common in advance-fee scams"),
        new Signal(Pattern.compile("(bank account (number|details)|routing number|ssn|social security number)", Pattern.CASE_INSENSITIVE),
            30, "Asks for sensitive banking or identity details up front"),
        new Signal(Pattern.compile("(pyramid|be your own boss|recruit (your )?(friends|others)|unlimited earning potential)", Pattern.CASE_INSENSITIVE),
            25, "Uses pyramid/MLM-style recruiting language"),
        new Signal(Pattern.compile("(no experience (needed|required|necessary)).{0,40}(\\$|usd)\\s?[2-9][0-9]{2,}", Pattern.CASE_INSENSITIVE),
            25, "Promises high pay for no experience or skills"),
        new Signal(Pattern.compile("(guaranteed income|guaranteed \\$|easy money|get rich)", Pattern.CASE_INSENSITIVE),
            20, "Uses \"guaranteed income\" / get-rich-quick phrasing"),
        new Signal(Pattern.compile("(telegram|whatsapp only|text (this|the following) number)", Pattern.CASE_INSENSITIVE),
            20, "Pushes contact off-platform to Telegram/WhatsApp/SMS"),
        new Signal(Pattern.compile("(immediate hire|no interview (needed|required))", Pattern.CASE_INSENSITIVE),
            15, "Claims hiring with no interview or screening at all")
    );

    public Map<String, Object> scan(JobPosting job) {
        String haystack = String.join(" \n ",
            nullToEmpty(job.getTitle()), nullToEmpty(job.getDescription()),
            nullToEmpty(job.getCompany()), nullToEmpty(job.getSalaryRange()));

        List<Map<String, Object>> flags = new ArrayList<>();
        int score = 0;
        for (Signal signal : signals) {
            if (signal.pattern().matcher(haystack).find()) {
                score += signal.weight();
                Map<String, Object> flag = new LinkedHashMap<>();
                flag.put("reason", signal.reason());
                flags.add(flag);
            }
        }
        score = Math.min(score, 100);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("riskScore", score);
        result.put("riskLevel", riskLevel(score));
        result.put("flags", flags);
        return result;
    }

    public Map<Long, Map<String, Object>> scanAll(List<JobPosting> jobs) {
        Map<Long, Map<String, Object>> results = new LinkedHashMap<>();
        for (JobPosting job : jobs) {
            results.put(job.getId(), scan(job));
        }
        return results;
    }

    private String riskLevel(int score) {
        if (score >= 50) return RISK_HIGH;
        if (score >= 20) return RISK_MEDIUM;
        return RISK_LOW;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
