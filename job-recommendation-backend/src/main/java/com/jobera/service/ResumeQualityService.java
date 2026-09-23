package com.jobera.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.jobera.entity.Resume;

/**
 * A checklist-style quality pass over an already-parsed resume - not a rewrite or an ML review,
 * just the same kind of concrete, checkable things a human reviewer would flag first: missing
 * contact info, no quantified achievements, weak/passive wording, a resume that's too short or
 * too long. Everything here works off text and fields the resume pipeline already extracts.
 */
@Service
public class ResumeQualityService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE_PATTERN =
        Pattern.compile("(\\+?\\d{1,3}[-.\\s]?)?\\(?\\d{3}\\)?[-.\\s]?\\d{3}[-.\\s]?\\d{4}");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d");

    private static final int MIN_WORD_COUNT = 150;
    private static final int MAX_WORD_COUNT = 1200;
    private static final int MIN_SKILLS = 3;
    private static final int MIN_ACTION_VERBS = 3;

    private static final List<String> WEAK_PHRASES = List.of(
        "responsible for", "duties included", "worked on", "helped with", "in charge of", "tasked with"
    );

    private static final List<String> ACTION_VERBS = List.of(
        "led", "built", "designed", "implemented", "improved", "achieved", "created", "developed",
        "launched", "managed", "delivered", "reduced", "increased", "optimized", "automated",
        "architected", "spearheaded", "streamlined", "mentored", "negotiated"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> analyze(Resume resume) {
        String text = resume.getParsedText() != null ? resume.getParsedText() : "";
        String lowerText = text.toLowerCase();

        List<Map<String, Object>> checks = new ArrayList<>();

        int wordCount = countWords(text);
        checks.add(check("length", wordCount >= MIN_WORD_COUNT && wordCount <= MAX_WORD_COUNT,
            wordCount < MIN_WORD_COUNT
                ? "Resume looks quite short (" + wordCount + " words) - there may not be enough detail here for a recruiter to evaluate you."
                : wordCount > MAX_WORD_COUNT
                    ? "Resume is quite long (" + wordCount + " words) - consider trimming to your most relevant experience."
                    : "Resume length looks reasonable (" + wordCount + " words)."));

        boolean hasEmail = EMAIL_PATTERN.matcher(text).find();
        checks.add(check("contact_email", hasEmail,
            hasEmail ? "Email address found." : "No email address found - add one so recruiters can reach you."));

        boolean hasPhone = PHONE_PATTERN.matcher(text).find();
        checks.add(check("contact_phone", hasPhone,
            hasPhone ? "Phone number found." : "No phone number found - add one so recruiters can reach you."));

        int skillCount = countSkills(resume.getSkills());
        checks.add(check("skills_listed", skillCount >= MIN_SKILLS,
            skillCount >= MIN_SKILLS
                ? skillCount + " skills detected."
                : "Few or no skills detected - list the specific tools and technologies you've used."));

        boolean hasEducation = resume.getEducationLevel() != null && !resume.getEducationLevel().isBlank()
            && !"Not specified".equalsIgnoreCase(resume.getEducationLevel());
        checks.add(check("education", hasEducation,
            hasEducation
                ? "Education detected (" + resume.getEducationLevel() + ")."
                : "No education details detected - add your highest qualification."));

        boolean hasQuantifiedAchievements = DIGIT_PATTERN.matcher(text).find();
        checks.add(check("quantified_achievements", hasQuantifiedAchievements,
            hasQuantifiedAchievements
                ? "Found numbers or metrics in your resume."
                : "No numbers or metrics found - consider quantifying your impact, e.g. \"improved load time by 30%\"."));

        long weakPhraseCount = WEAK_PHRASES.stream().filter(lowerText::contains).count();
        checks.add(check("weak_phrases", weakPhraseCount == 0,
            weakPhraseCount == 0
                ? "No overly passive phrasing detected."
                : "Found passive phrasing like \"responsible for\" - try leading with a strong action verb instead."));

        long actionVerbCount = ACTION_VERBS.stream().filter(lowerText::contains).count();
        checks.add(check("action_verbs", actionVerbCount >= MIN_ACTION_VERBS,
            actionVerbCount >= MIN_ACTION_VERBS
                ? "Good use of action verbs (" + actionVerbCount + " found)."
                : "Few action verbs found - start bullet points with words like \"built\", \"led\" or \"improved\"."));

        long passed = checks.stream().filter(c -> (boolean) c.get("passed")).count();
        int score = (int) Math.round(100.0 * passed / checks.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("wordCount", wordCount);
        result.put("checks", checks);
        return result;
    }

    private Map<String, Object> check(String id, boolean passed, String message) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("id", id);
        check.put("passed", passed);
        check.put("message", message);
        return check;
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private int countSkills(String skillsJson) {
        if (skillsJson == null || skillsJson.isBlank()) {
            return 0;
        }
        try {
            Set<String> skills = objectMapper.readValue(skillsJson, new TypeReference<Set<String>>() {});
            return skills.size();
        } catch (Exception e) {
            return 0;
        }
    }
}
