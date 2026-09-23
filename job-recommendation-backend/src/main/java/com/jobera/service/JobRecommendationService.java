package com.jobera.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobera.entity.JobPosting;
import com.jobera.entity.Resume;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobRecommendationService {
    
    private static final Logger logger = LoggerFactory.getLogger(JobRecommendationService.class);
    
    // MINIMUM MATCH THRESHOLD - ONLY SHOW JOBS WITH >= 50% MATCH
    private static final double MIN_MATCH_THRESHOLD = 50.0;
    
    private final JobPostingService jobPostingService;
    private final ResumeService resumeService;
    private final ObjectMapper objectMapper;
    
    public JobRecommendationService(JobPostingService jobPostingService, 
                                   ResumeService resumeService) {
        this.jobPostingService = jobPostingService;
        this.resumeService = resumeService;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * MAIN RECOMMENDATION METHOD - FIXED: Only returns jobs with actual skill matches
     * Returns empty list if no matching jobs found
     */
    public List<JobPosting> recommendJobsForUser(Long userId) {
        logger.info("=== RECOMMENDING JOBS FOR USER: {} ===", userId);
        
        // Get user's latest resume
        Optional<Resume> latestResume = resumeService.getLatestResumeByUserId(userId);
        
        // If no resume found, return empty list - NO RECOMMENDATIONS
        if (latestResume.isEmpty()) {
            logger.info("❌ No resume found for user: {}. Returning empty recommendations.", userId);
            return new ArrayList<>();
        }
        
        Resume resume = latestResume.get();
        Set<String> userSkills = extractSkillsFromResume(resume);
        
        // If no skills extracted, return empty list - NO RECOMMENDATIONS
        if (userSkills.isEmpty()) {
            logger.info("❌ No skills extracted for user: {}. Returning empty recommendations.", userId);
            return new ArrayList<>();
        }
        
        logger.info("✅ User {} has {} skills: {}", userId, userSkills.size(), userSkills);
        
        // Get all active jobs (filter out internships)
        List<JobPosting> allJobs = jobPostingService.getActiveJobPostings();
        List<JobPosting> onlyJobs = allJobs.stream()
            .filter(job -> job.getJobType() != null && 
                   !"Internship".equalsIgnoreCase(job.getJobType()))
            .collect(Collectors.toList());
        
        logger.info("Total active jobs (excluding internships): {}", onlyJobs.size());
        
        // Calculate match scores and filter by threshold
        List<ScoredJob> scoredJobs = new ArrayList<>();
        for (JobPosting job : onlyJobs) {
            double matchScore = calculateJobMatchScore(job, userSkills);
            
            // CRITICAL FIX: Only add jobs with match score >= threshold
            if (matchScore >= MIN_MATCH_THRESHOLD) {
                scoredJobs.add(new ScoredJob(job, matchScore));
                logger.info("✓ Job '{}' matched: {:.0f}%", job.getTitle(), matchScore);
            } else {
                logger.info("✗ Job '{}' below threshold ({:.0f}% < {}%)", 
                    job.getTitle(), matchScore, MIN_MATCH_THRESHOLD);
            }
        }
        
        // Sort by match score (highest first)
        scoredJobs.sort((a, b) -> Double.compare(b.score, a.score));
        
        int matchCount = scoredJobs.size();
        logger.info("📊 Found {} jobs matching user skills (threshold: {}%)", matchCount, MIN_MATCH_THRESHOLD);
        
        if (matchCount == 0) {
            logger.info("⚠️ No jobs match user's skills. Returning empty list.");
        }
        
        return scoredJobs.stream()
            .map(scored -> scored.job)
            .collect(Collectors.toList());
    }
    
    /**
     * Calculate job match score based on skills
     * Returns 0-100 percentage
     */
    private double calculateJobMatchScore(JobPosting job, Set<String> userSkills) {
        try {
            Set<String> requiredSkills = parseSkillsFromJson(job.getRequiredSkills());
            
            if (requiredSkills.isEmpty()) {
                logger.debug("Job {} has no required skills specified", job.getId());
                return 0;
            }
            
            // Calculate matched skills count with fuzzy matching
            Set<String> matchedSkills = new HashSet<>();
            for (String reqSkill : requiredSkills) {
                for (String userSkill : userSkills) {
                    if (isSkillMatch(reqSkill, userSkill)) {
                        matchedSkills.add(reqSkill);
                        break;
                    }
                }
            }
            
            // Calculate match percentage
            double matchPercentage = (double) matchedSkills.size() / requiredSkills.size() * 100;
            
            // Round to 1 decimal place
            matchPercentage = Math.round(matchPercentage * 10.0) / 10.0;
            
            logger.debug("Job '{}': Required skills={}, Matched={}, Score={}%", 
                job.getTitle(), requiredSkills.size(), matchedSkills.size(), matchPercentage);
            
            return matchPercentage;
            
        } catch (Exception e) {
            logger.error("Error calculating match score for job {}: {}", job.getId(), e.getMessage());
            return 0;
        }
    }
    
    /**
     * Skill groups that should be treated as the same skill regardless of how each side
     * wrote it. Looked up via {@link #SKILL_TO_GROUP}, built once from this list.
     */
    private static final List<Set<String>> SKILL_SYNONYM_GROUPS = List.of(
        Set.of("javascript", "js", "ecmascript"),
        Set.of("typescript", "ts"),
        Set.of("node", "nodejs", "node.js"),
        Set.of("react", "reactjs", "react.js"),
        Set.of("vue", "vuejs", "vue.js"),
        Set.of("angular", "angularjs"),
        Set.of("next", "nextjs", "next.js"),
        Set.of("spring boot", "springboot", "spring-boot"),
        Set.of("spring", "springframework"),
        Set.of("python", "py"),
        Set.of("html", "html5"),
        Set.of("css", "css3"),
        Set.of("sass", "scss"),
        Set.of("rest", "restapi", "restful", "rest api"),
        Set.of("graphql", "graph ql"),
        Set.of("mongodb", "mongo"),
        Set.of("postgresql", "postgres", "psql"),
        Set.of("mysql", "my sql"),
        Set.of("docker", "containerization"),
        Set.of("kubernetes", "k8s"),
        Set.of("aws", "amazon web services"),
        Set.of("gcp", "google cloud", "google cloud platform"),
        Set.of("azure", "microsoft azure"),
        Set.of("ci cd", "cicd", "ci/cd", "continuous integration"),
        Set.of("machine learning", "ml"),
        Set.of("artificial intelligence", "ai"),
        Set.of("data science", "ds"),
        Set.of("natural language processing", "nlp"),
        Set.of("deep learning", "dl"),
        Set.of("git", "github", "version control"),
        Set.of("c sharp", "c#", "csharp"),
        Set.of("c plus plus", "c++", "cpp"),
        Set.of("golang", "go"),
        Set.of("dotnet", ".net", "asp.net"),
        Set.of("ui ux", "ui/ux", "user interface design"),
        Set.of("qa", "quality assurance", "testing"),
        Set.of("oop", "object oriented programming"),
        Set.of("api", "apis"),
        Set.of("sql", "structured query language"),
        Set.of("nosql", "no sql")
    );

    private static final Map<String, Set<String>> SKILL_TO_GROUP = buildSkillToGroupIndex();

    private static Map<String, Set<String>> buildSkillToGroupIndex() {
        Map<String, Set<String>> index = new HashMap<>();
        for (Set<String> group : SKILL_SYNONYM_GROUPS) {
            for (String member : group) {
                index.put(member, group);
            }
        }
        return index;
    }

    /** Minimum token length before a small edit distance is trusted as a typo rather than a different word. */
    private static final int MIN_FUZZY_TOKEN_LENGTH = 5;

    /**
     * True when a job's required skill and a candidate's listed skill should count as the same
     * thing. Checks, in order: exact match, a shared synonym group (e.g. "js"/"javascript"),
     * an exact shared word after splitting on punctuation (so "Spring Boot" matches "spring-boot"
     * without "Java" ever matching "JavaScript" the way naive substring matching would), and
     * finally a bounded edit-distance check to catch typos in longer skill names.
     */
    boolean isSkillMatch(String jobSkill, String userSkill) {
        String js = normalizeSkill(jobSkill);
        String us = normalizeSkill(userSkill);
        if (js.isEmpty() || us.isEmpty()) {
            return false;
        }
        if (js.equals(us)) {
            return true;
        }
        if (shareASynonymGroup(js, us)) {
            return true;
        }

        Set<String> jsTokens = tokenize(js);
        Set<String> usTokens = tokenize(us);
        for (String token : jsTokens) {
            if (usTokens.contains(token)) {
                return true;
            }
        }
        for (String jsToken : jsTokens) {
            for (String usToken : usTokens) {
                if (shareASynonymGroup(jsToken, usToken) || isTypoOfEachOther(jsToken, usToken)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean shareASynonymGroup(String a, String b) {
        Set<String> group = SKILL_TO_GROUP.get(a);
        return group != null && group.contains(b);
    }

    private boolean isTypoOfEachOther(String a, String b) {
        if (a.length() < MIN_FUZZY_TOKEN_LENGTH || b.length() < MIN_FUZZY_TOKEN_LENGTH) {
            return false;
        }
        int maxLen = Math.max(a.length(), b.length());
        int threshold = Math.min(3, maxLen / 5);
        return levenshteinDistance(a, b) <= threshold;
    }

    private String normalizeSkill(String skill) {
        return skill == null ? "" : skill.toLowerCase().trim();
    }

    private static Set<String> tokenize(String normalized) {
        Set<String> tokens = new HashSet<>();
        for (String token : normalized.split("[^a-z0-9#+]+")) {
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /** Classic Levenshtein edit distance (insert/delete/substitute), O(n*m). */
    private static int levenshteinDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
    
    /**
     * Extract skills from resume
     */
    private Set<String> extractSkillsFromResume(Resume resume) {
        Set<String> extractedSkills = new HashSet<>();
        
        try {
            // First try JSON skills
            if (resume.getSkills() != null && !resume.getSkills().isEmpty()) {
                try {
                    Set<String> jsonSkills = objectMapper.readValue(resume.getSkills(), 
                        new TypeReference<Set<String>>() {});
                    extractedSkills.addAll(jsonSkills);
                    logger.info("Extracted {} skills from JSON", jsonSkills.size());
                } catch (IOException e) {
                    logger.info("JSON parsing failed, trying text extraction");
                }
            }
        } catch (Exception e) {
            logger.info("Error parsing skills JSON: {}", e.getMessage());
        }
        
        // Normalize skills
        Set<String> normalizedSkills = new HashSet<>();
        for (String skill : extractedSkills) {
            String normalized = skill.toLowerCase().trim().replaceAll("[^a-zA-Z0-9#+. ]", "");
            if (normalized.length() > 1 && !normalized.matches("\\d+")) { // Exclude pure numbers
                normalizedSkills.add(normalized);
            }
        }
        
        logger.info("Final extracted skills ({}): {}", normalizedSkills.size(), normalizedSkills);
        return normalizedSkills;
    }
    
    /**
     * Parse skills from JSON string
     */
    private Set<String> parseSkillsFromJson(String skillsJson) {
        try {
            if (skillsJson != null && !skillsJson.isEmpty()) {
                return objectMapper.readValue(skillsJson, new TypeReference<Set<String>>() {});
            }
        } catch (IOException e) {
            logger.warn("Failed to parse skills JSON: {}", skillsJson);
        }
        return new HashSet<>();
    }
    
    /**
     * Get detailed recommendation analysis with match percentages
     */
    public Map<String, Object> getDetailedRecommendationAnalysis(Long userId) {
        Map<String, Object> analysis = new HashMap<>();
        
        // Get user's latest resume
        Optional<Resume> latestResume = resumeService.getLatestResumeByUserId(userId);
        
        Set<String> userSkills = new HashSet<>();
        if (latestResume.isPresent()) {
            userSkills = extractSkillsFromResume(latestResume.get());
        }
        
        analysis.put("userSkills", new ArrayList<>(userSkills));
        analysis.put("totalSkills", userSkills.size());
        
        // Get recommendations (only matches above threshold)
        List<JobPosting> recommendations = recommendJobsForUser(userId);
        analysis.put("recommendedJobsCount", recommendations.size());
        analysis.put("hasRecommendations", !recommendations.isEmpty());
        
        List<Map<String, Object>> detailedRecommendations = new ArrayList<>();
        for (JobPosting job : recommendations) {
            Map<String, Object> jobDetails = new LinkedHashMap<>();
            jobDetails.put("jobId", job.getId());
            jobDetails.put("title", job.getTitle());
            jobDetails.put("company", job.getCompany());
            jobDetails.put("location", job.getLocation());
            jobDetails.put("salaryRange", job.getSalaryRange());
            jobDetails.put("jobType", job.getJobType());
            jobDetails.put("experienceLevel", job.getExperienceLevel());
            
            Set<String> requiredSkills = parseSkillsFromJson(job.getRequiredSkills());
            jobDetails.put("requiredSkills", new ArrayList<>(requiredSkills));
            
            // Calculate matched and missing skills
            List<String> matchedSkills = new ArrayList<>();
            List<String> missingSkills = new ArrayList<>();
            
            for (String reqSkill : requiredSkills) {
                boolean matched = false;
                for (String userSkill : userSkills) {
                    if (isSkillMatch(reqSkill, userSkill)) {
                        matchedSkills.add(reqSkill);
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    missingSkills.add(reqSkill);
                }
            }
            
            double matchScore = requiredSkills.isEmpty() ? 0 : 
                (double) matchedSkills.size() / requiredSkills.size() * 100;
            matchScore = Math.round(matchScore * 10.0) / 10.0;
            
            jobDetails.put("matchScore", matchScore);
            jobDetails.put("matchedSkills", matchedSkills);
            jobDetails.put("missingSkills", missingSkills);
            
            detailedRecommendations.add(jobDetails);
        }
        
        // Sort by match score descending
        detailedRecommendations.sort((a, b) -> 
            Double.compare((Double) b.get("matchScore"), (Double) a.get("matchScore")));
        
        analysis.put("detailedRecommendations", detailedRecommendations);
        
        // Add message if no recommendations
        if (detailedRecommendations.isEmpty()) {
            analysis.put("message", "No jobs match your skills. Try uploading a resume with skills that match available job requirements.");
        }
        
        return analysis;
    }
    
    /**
     * Recommend jobs by skills - FIXED: Only returns matches above threshold
     */
    public List<JobPosting> recommendJobsBySkills(Set<String> skills) {
        if (skills == null || skills.isEmpty()) {
            logger.info("No skills provided for recommendation");
            return new ArrayList<>();
        }
        
        List<JobPosting> allJobs = jobPostingService.getActiveJobPostings();
        
        List<ScoredJob> scoredJobs = new ArrayList<>();
        for (JobPosting job : allJobs) {
            double score = calculateJobMatchScore(job, skills);
            if (score >= MIN_MATCH_THRESHOLD) {
                scoredJobs.add(new ScoredJob(job, score));
            }
        }
        
        scoredJobs.sort((a, b) -> Double.compare(b.score, a.score));
        
        logger.info("Found {} jobs matching provided skills (threshold: {}%)", 
            scoredJobs.size(), MIN_MATCH_THRESHOLD);
        
        return scoredJobs.stream()
            .map(scored -> scored.job)
            .collect(Collectors.toList());
    }
    
    /**
     * Helper class for scored jobs
     */
    private static class ScoredJob {
        JobPosting job;
        double score;
        
        ScoredJob(JobPosting job, double score) {
            this.job = job;
            this.score = score;
        }
    }
}