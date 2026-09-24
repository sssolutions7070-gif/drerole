package com.jobera.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.jobera.entity.InterviewExperience;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.InterviewExperienceRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.UserRepository;

/** What it's actually like interviewing at a company - process, difficulty, outcome - shared by
 *  candidates who've been through it, separate from CompanyReview's overall star rating. */
@Service
public class InterviewExperienceService {

    private static final Set<String> VALID_OUTCOMES = Set.of("OFFER", "REJECTED", "NO_RESPONSE", "WITHDREW");

    public static class ExperienceException extends RuntimeException {
        public ExperienceException(String message) {
            super(message);
        }
    }

    private final InterviewExperienceRepository interviewExperienceRepository;
    private final RecruiterRepository recruiterRepository;
    private final UserRepository userRepository;

    public InterviewExperienceService(InterviewExperienceRepository interviewExperienceRepository,
                                       RecruiterRepository recruiterRepository,
                                       UserRepository userRepository) {
        this.interviewExperienceRepository = interviewExperienceRepository;
        this.recruiterRepository = recruiterRepository;
        this.userRepository = userRepository;
    }

    /** New or edited reports always (re-)enter the moderation queue - same reasoning as
     *  CompanyReview: the text itself is what's being moderated, so an edit hasn't been reviewed yet. */
    public InterviewExperience submitExperience(Long userId, Long recruiterId, Integer difficultyRating,
                                                 String outcome, String processDescription) {
        if (difficultyRating == null || difficultyRating < 1 || difficultyRating > 5) {
            throw new ExperienceException("Difficulty rating must be between 1 and 5");
        }
        if (outcome == null || !VALID_OUTCOMES.contains(outcome)) {
            throw new ExperienceException("Unknown outcome: " + outcome);
        }
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ExperienceException("User not found: " + userId));
        Recruiter recruiter = recruiterRepository.findById(recruiterId)
            .orElseThrow(() -> new ExperienceException("Company not found: " + recruiterId));

        InterviewExperience experience = interviewExperienceRepository.findByUserIdAndRecruiterId(userId, recruiterId)
            .orElseGet(() -> new InterviewExperience(user, recruiter, difficultyRating, outcome, processDescription));
        experience.setDifficultyRating(difficultyRating);
        experience.setOutcome(outcome);
        experience.setProcessDescription(processDescription);
        experience.setModerationStatus(InterviewExperience.MODERATION_PENDING);
        experience.setRejectionReason(null);

        return interviewExperienceRepository.save(experience);
    }

    public Optional<InterviewExperience> getMyExperience(Long userId, Long recruiterId) {
        return interviewExperienceRepository.findByUserIdAndRecruiterId(userId, recruiterId);
    }

    /** Approved reports for a company, plus the average difficulty - what the public company
     *  profile page shows. */
    public Map<String, Object> getPublicExperiences(Long recruiterId) {
        List<InterviewExperience> approved = interviewExperienceRepository
            .findByRecruiterIdAndModerationStatusOrderByCreatedAtDesc(recruiterId, InterviewExperience.MODERATION_APPROVED);

        double averageDifficulty = approved.stream().mapToInt(InterviewExperience::getDifficultyRating).average().orElse(0.0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("averageDifficulty", Math.round(averageDifficulty * 10.0) / 10.0);
        result.put("totalExperiences", approved.size());
        result.put("experiences", approved.stream().map(this::toPublicView).toList());
        return result;
    }

    private Map<String, Object> toPublicView(InterviewExperience experience) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", experience.getId());
        view.put("difficultyRating", experience.getDifficultyRating());
        view.put("outcome", experience.getOutcome());
        view.put("processDescription", experience.getProcessDescription());
        view.put("createdAt", experience.getCreatedAt());
        // The reporter's identity is deliberately left off, same as company reviews - honesty
        // about a real interview process shouldn't cost a candidate their anonymity.
        return view;
    }

    public List<InterviewExperience> getPendingExperiences() {
        return interviewExperienceRepository.findByModerationStatusOrderByCreatedAtDesc(InterviewExperience.MODERATION_PENDING);
    }

    public InterviewExperience approveExperience(Long experienceId) {
        InterviewExperience experience = interviewExperienceRepository.findById(experienceId)
            .orElseThrow(() -> new ExperienceException("Interview experience not found: " + experienceId));
        experience.setModerationStatus(InterviewExperience.MODERATION_APPROVED);
        experience.setRejectionReason(null);
        return interviewExperienceRepository.save(experience);
    }

    public InterviewExperience rejectExperience(Long experienceId, String reason) {
        InterviewExperience experience = interviewExperienceRepository.findById(experienceId)
            .orElseThrow(() -> new ExperienceException("Interview experience not found: " + experienceId));
        experience.setModerationStatus(InterviewExperience.MODERATION_REJECTED);
        experience.setRejectionReason(reason);
        return interviewExperienceRepository.save(experience);
    }
}
