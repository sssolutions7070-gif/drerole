package com.jobera.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.jobera.entity.User;
import com.jobera.entity.UserSkill;
import com.jobera.repository.ResumeRepository;
import com.jobera.repository.UserRepository;
import com.jobera.repository.UserSkillRepository;

/** Lets a recruiter search candidates who've opted in to be "open to work" (User.openToWork),
 *  instead of only ever seeing people who happened to apply to one of their own postings - a
 *  passive-candidate pool any recruiter on the platform can browse. */
@Service
public class CandidateDiscoveryService {

    private final UserRepository userRepository;
    private final UserSkillRepository userSkillRepository;
    private final ResumeRepository resumeRepository;

    public CandidateDiscoveryService(UserRepository userRepository,
                                      UserSkillRepository userSkillRepository,
                                      ResumeRepository resumeRepository) {
        this.userRepository = userRepository;
        this.userSkillRepository = userSkillRepository;
        this.resumeRepository = resumeRepository;
    }

    public List<Map<String, Object>> discover(String skill, String location) {
        return userRepository.findByOpenToWorkTrueAndIsActiveTrue().stream()
            .map(this::toCandidateView)
            .filter(view -> matchesSkill(view, skill))
            .filter(view -> matchesLocation(view, location))
            .collect(Collectors.toList());
    }

    private boolean matchesSkill(Map<String, Object> view, String skill) {
        if (skill == null || skill.isBlank()) {
            return true;
        }
        @SuppressWarnings("unchecked")
        List<String> skills = (List<String>) view.get("skills");
        String needle = skill.trim().toLowerCase();
        return skills.stream().anyMatch(s -> s.toLowerCase().contains(needle));
    }

    private boolean matchesLocation(Map<String, Object> view, String location) {
        if (location == null || location.isBlank()) {
            return true;
        }
        Object candidateLocation = view.get("location");
        return candidateLocation != null && candidateLocation.toString().toLowerCase().contains(location.trim().toLowerCase());
    }

    private Map<String, Object> toCandidateView(User user) {
        List<String> skills = userSkillRepository.findByUserId(user.getId()).stream()
            .map(UserSkill::getSkillName)
            .collect(Collectors.toList());
        boolean hasResume = !resumeRepository.findByUserId(user.getId()).isEmpty();

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("userId", user.getId());
        view.put("fullName", user.getFullName());
        view.put("headline", user.getHeadline());
        view.put("bio", user.getBio());
        view.put("location", user.getLocation());
        view.put("skills", skills);
        view.put("hasResume", hasResume);
        return view;
    }
}
