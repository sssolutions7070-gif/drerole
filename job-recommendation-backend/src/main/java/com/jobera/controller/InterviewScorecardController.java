package com.jobera.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.InterviewRound;
import com.jobera.entity.InterviewScorecard;
import com.jobera.entity.User;
import com.jobera.service.InterviewRoundService;
import com.jobera.service.InterviewScorecardService;

import jakarta.servlet.http.HttpServletRequest;

/** Structured per-round interview feedback, recruiter-only (a candidate never sees the internal
 *  rubric a recruiter filled in about them) - gated by AccessPolicy's recruiterManagesRound()/
 *  recruiterManagesApplication() checks. */
@RestController
public class InterviewScorecardController {

    private static final String ERROR_KEY = "error";

    private final InterviewScorecardService scorecardService;
    private final InterviewRoundService interviewRoundService;

    public InterviewScorecardController(InterviewScorecardService scorecardService,
                                         InterviewRoundService interviewRoundService) {
        this.scorecardService = scorecardService;
        this.interviewRoundService = interviewRoundService;
    }

    private User callingUser(HttpServletRequest request) {
        return (User) request.getAttribute(AuthInterceptor.AUTHENTICATED_USER);
    }

    @PutMapping("/api/interview-rounds/{roundId}/scorecard")
    public ResponseEntity<?> submit(@PathVariable Long roundId, @RequestBody Map<String, Object> payload,
                                     HttpServletRequest request) {
        try {
            InterviewScorecard scorecard = scorecardService.submitScorecard(roundId, callingUser(request).getId(),
                asInteger(payload.get("communicationRating")),
                asInteger(payload.get("technicalRating")),
                asInteger(payload.get("cultureFitRating")),
                (String) payload.get("recommendation"),
                (String) payload.get("notes"));
            return ResponseEntity.ok(scorecard);
        } catch (InterviewScorecardService.RoundNotFoundException | InterviewScorecardService.InvalidRatingException
                 | InterviewScorecardService.InvalidRecommendationException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @GetMapping("/api/interview-rounds/{roundId}/scorecard")
    public ResponseEntity<?> get(@PathVariable Long roundId) {
        try {
            return ResponseEntity.ok(scorecardService.getScorecard(roundId));
        } catch (InterviewScorecardService.ScorecardNotFoundException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    @GetMapping("/api/applications/{applicationId}/scorecards")
    public ResponseEntity<?> getForApplication(@PathVariable Long applicationId) {
        try {
            List<InterviewRound> rounds = interviewRoundService.listRounds(applicationId);
            List<Long> roundIds = rounds.stream().map(InterviewRound::getId).toList();
            Map<Long, InterviewScorecard> scorecardsByRoundId = new LinkedHashMap<>();
            scorecardService.getScorecardsForRounds(roundIds)
                .forEach(sc -> scorecardsByRoundId.put(sc.getInterviewRoundId(), sc));

            List<Map<String, Object>> pipeline = rounds.stream().map(round -> {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("roundId", round.getId());
                entry.put("roundNumber", round.getRoundNumber());
                entry.put("title", round.getTitle());
                entry.put("roundType", round.getRoundType());
                entry.put("status", round.getStatus());
                entry.put("scorecard", scorecardsByRoundId.get(round.getId()));
                return entry;
            }).toList();

            return ResponseEntity.ok(pipeline);
        } catch (InterviewRoundService.ApplicationNotFoundException e) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, e.getMessage()));
        }
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
