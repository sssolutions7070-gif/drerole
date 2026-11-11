package com.jobera.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.jobera.entity.Resume;
import com.jobera.entity.User;
import com.jobera.repository.ResumeRepository;
import com.jobera.repository.SkillBadgeRepository;
import com.jobera.repository.UserCertificationRepository;
import com.jobera.repository.UserEducationRepository;
import com.jobera.repository.UserExperienceRepository;
import com.jobera.repository.UserRepository;
import com.jobera.repository.UserSkillRepository;

/**
 * A single "how complete/attractive is my profile" score for a candidate's own dashboard,
 * checklist-style like ResumeQualityService (which this reuses for the one check that's about
 * the resume's own content, rather than re-implementing that logic). Every other check here is
 * about *profile* completeness - the structured fields a recruiter sees before ever opening a
 * resume: headline, bio, links, and the counts of skills/education/experience/certifications/
 * badges already recorded elsewhere in the app.
 */
@Service
public class ProfileStrengthService {

    private static final int MIN_BIO_LENGTH = 20;

    private final UserRepository userRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserEducationRepository userEducationRepository;
    private final UserExperienceRepository userExperienceRepository;
    private final UserCertificationRepository userCertificationRepository;
    private final SkillBadgeRepository skillBadgeRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeQualityService resumeQualityService;

    public ProfileStrengthService(UserRepository userRepository,
                                   UserSkillRepository userSkillRepository,
                                   UserEducationRepository userEducationRepository,
                                   UserExperienceRepository userExperienceRepository,
                                   UserCertificationRepository userCertificationRepository,
                                   SkillBadgeRepository skillBadgeRepository,
                                   ResumeRepository resumeRepository,
                                   ResumeQualityService resumeQualityService) {
        this.userRepository = userRepository;
        this.userSkillRepository = userSkillRepository;
        this.userEducationRepository = userEducationRepository;
        this.userExperienceRepository = userExperienceRepository;
        this.userCertificationRepository = userCertificationRepository;
        this.skillBadgeRepository = skillBadgeRepository;
        this.resumeRepository = resumeRepository;
        this.resumeQualityService = resumeQualityService;
    }

    public static class UserNotFoundException extends RuntimeException {
        public UserNotFoundException(Long userId) {
            super("User not found: " + userId);
        }
    }

    public Map<String, Object> calculate(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

        List<Map<String, Object>> checks = new ArrayList<>();

        boolean hasHeadline = user.getHeadline() != null && !user.getHeadline().isBlank();
        checks.add(check("headline", hasHeadline,
            hasHeadline ? "Headline is set." : "Add a headline - a one-line summary of what you do."));

        boolean hasBio = user.getBio() != null && user.getBio().trim().length() >= MIN_BIO_LENGTH;
        checks.add(check("bio", hasBio,
            hasBio ? "Bio is filled in." : "Add a short bio so recruiters get a sense of who you are."));

        boolean hasLink = (user.getWebsite() != null && !user.getWebsite().isBlank())
            || (user.getLinkedin() != null && !user.getLinkedin().isBlank())
            || (user.getGithub() != null && !user.getGithub().isBlank());
        checks.add(check("links", hasLink,
            hasLink ? "At least one external link is linked." : "Add a LinkedIn, GitHub, or portfolio link."));

        int skillCount = userSkillRepository.findByUserId(userId).size();
        checks.add(check("skills_listed", skillCount >= 3,
            skillCount >= 3 ? skillCount + " skills listed." : "List at least 3 skills on your profile."));

        boolean hasEducation = !userEducationRepository.findByUserId(userId).isEmpty();
        checks.add(check("education", hasEducation,
            hasEducation ? "Education added." : "Add your education history."));

        boolean hasExperience = !userExperienceRepository.findByUserId(userId).isEmpty();
        checks.add(check("experience", hasExperience,
            hasExperience ? "Work experience added." : "Add your work experience."));

        boolean hasCertification = !userCertificationRepository.findByUserId(userId).isEmpty();
        checks.add(check("certifications", hasCertification,
            hasCertification ? "At least one certification added." : "Add a certification if you have one - optional, but it stands out."));

        int badgeCount = skillBadgeRepository.findByUserIdOrderByEarnedAtDesc(userId).size();
        checks.add(check("badges_earned", badgeCount > 0,
            badgeCount > 0 ? badgeCount + " skill badge" + (badgeCount == 1 ? "" : "s") + " earned."
                : "Take a practice test to earn a verified skill badge."));

        Optional<Resume> latestResume = resumeRepository.findFirstByUserIdOrderByUploadDateDesc(userId);
        Integer resumeQualityScore = null;
        if (latestResume.isPresent()) {
            Map<String, Object> resumeQuality = resumeQualityService.analyze(latestResume.get());
            resumeQualityScore = (Integer) resumeQuality.get("score");
            boolean resumeGood = resumeQualityScore >= 60;
            checks.add(check("resume_quality", resumeGood,
                resumeGood ? "Resume quality score is " + resumeQualityScore + "/100."
                    : "Your resume quality score is " + resumeQualityScore + "/100 - see your resume checklist for specific fixes."));
        } else {
            checks.add(check("resume_quality", false, "Upload a resume so recruiters (and this score) can evaluate it."));
        }

        long passed = checks.stream().filter(c -> (boolean) c.get("passed")).count();
        int score = (int) Math.round(100.0 * passed / checks.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", score);
        result.put("checks", checks);
        result.put("resumeQualityScore", resumeQualityScore);
        return result;
    }

    private Map<String, Object> check(String id, boolean passed, String message) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("id", id);
        check.put("passed", passed);
        check.put("message", message);
        return check;
    }
}
