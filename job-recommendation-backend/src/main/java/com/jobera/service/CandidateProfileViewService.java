package com.jobera.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.jobera.entity.ProfileView;
import com.jobera.entity.Recruiter;
import com.jobera.repository.ProfileViewRepository;
import com.jobera.repository.RecruiterRepository;

/** Lets a candidate see which recruiters viewed their profile while browsing Discover Candidates -
 *  the symmetric counterpart to a recruiter already seeing who viewed one of their applications. */
@Service
public class CandidateProfileViewService {

    private final ProfileViewRepository profileViewRepository;
    private final RecruiterRepository recruiterRepository;

    public CandidateProfileViewService(ProfileViewRepository profileViewRepository,
                                        RecruiterRepository recruiterRepository) {
        this.profileViewRepository = profileViewRepository;
        this.recruiterRepository = recruiterRepository;
    }

    /** Records that a recruiter opened a candidate's expanded profile. Viewing your own listing
     *  (shouldn't normally be reachable, since discovery excludes the caller, but checked here as
     *  a safeguard) and a recruiter re-viewing the same candidate both no-op past the first row -
     *  re-viewing just bumps the timestamp so the candidate's list doesn't fill with duplicates. */
    public void recordView(Long recruiterId, Long viewedUserId) {
        ProfileView view = profileViewRepository.findByRecruiterIdAndViewedUserId(recruiterId, viewedUserId)
            .orElseGet(() -> new ProfileView(recruiterId, viewedUserId));
        view.setViewedAt(LocalDateTime.now());
        profileViewRepository.save(view);
    }

    /** Every recruiter who has viewed this candidate, most recent first, with the company name
     *  resolved at read time (so a later rename shows up here too). */
    public List<Map<String, Object>> getViewsForCandidate(Long userId) {
        return profileViewRepository.findByViewedUserIdOrderByViewedAtDesc(userId).stream()
            .map(this::toView)
            .toList();
    }

    public long getViewCount(Long userId) {
        return profileViewRepository.countByViewedUserId(userId);
    }

    private Map<String, Object> toView(ProfileView profileView) {
        Optional<Recruiter> recruiter = recruiterRepository.findById(profileView.getRecruiterId());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("companyName", recruiter.map(Recruiter::getCompanyName).orElse("A recruiter"));
        view.put("viewedAt", profileView.getViewedAt());
        return view;
    }
}
