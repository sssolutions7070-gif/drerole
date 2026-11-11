package com.jobera.service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobera.entity.OutreachSequence;
import com.jobera.entity.Recruiter;
import com.jobera.entity.User;
import com.jobera.repository.DirectMessageRepository;
import com.jobera.repository.OutreachSequenceRepository;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.UserRepository;

/** A recruiter queues a multi-step automated message sequence to one sourced candidate (e.g.
 *  "Day 0: intro", "Day 3: follow-up"). Step 0 goes out immediately through DirectMessageService
 *  so it lands in the same thread the recruiter already messages that candidate from; later steps
 *  fire once their delay elapses, and the whole thing stops automatically the moment the
 *  candidate replies - see processDueSteps(), run hourly by OutreachSequenceScheduler. */
@Service
public class OutreachSequenceService {

    private static final Logger log = LoggerFactory.getLogger(OutreachSequenceService.class);

    private final OutreachSequenceRepository outreachSequenceRepository;
    private final RecruiterRepository recruiterRepository;
    private final UserRepository userRepository;
    private final DirectMessageService directMessageService;
    private final DirectMessageRepository directMessageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OutreachSequenceService(OutreachSequenceRepository outreachSequenceRepository,
                                    RecruiterRepository recruiterRepository,
                                    UserRepository userRepository,
                                    DirectMessageService directMessageService,
                                    DirectMessageRepository directMessageRepository) {
        this.outreachSequenceRepository = outreachSequenceRepository;
        this.recruiterRepository = recruiterRepository;
        this.userRepository = userRepository;
        this.directMessageService = directMessageService;
        this.directMessageRepository = directMessageRepository;
    }

    public static class RecruiterNotFoundException extends RuntimeException {
        public RecruiterNotFoundException(Long recruiterId) {
            super("Recruiter not found: " + recruiterId);
        }
    }

    public static class SequenceNotFoundException extends RuntimeException {
        public SequenceNotFoundException(Long id) {
            super("Outreach sequence not found: " + id);
        }
    }

    public static class InvalidStepsException extends RuntimeException {
        public InvalidStepsException() {
            super("A sequence needs at least one step");
        }
    }

    public static class SequenceAlreadyActiveException extends RuntimeException {
        public SequenceAlreadyActiveException() {
            super("This candidate already has an active outreach sequence from you");
        }
    }

    public OutreachSequence startSequence(Long recruiterId, Long candidateUserId, List<Map<String, Object>> steps) {
        Recruiter recruiter = recruiterRepository.findById(recruiterId)
            .orElseThrow(() -> new RecruiterNotFoundException(recruiterId));
        if (steps == null || steps.isEmpty()) {
            throw new InvalidStepsException();
        }
        if (outreachSequenceRepository.existsByRecruiterIdAndCandidateUserIdAndStatus(
                recruiterId, candidateUserId, OutreachSequence.STATUS_ACTIVE)) {
            throw new SequenceAlreadyActiveException();
        }

        OutreachSequence sequence = new OutreachSequence(recruiterId, candidateUserId, serialize(steps));

        sendStepMessage(recruiter, candidateUserId, steps.get(0));
        sequence.setCurrentStep(1);
        sequence.setLastStepSentAt(LocalDateTime.now());
        if (steps.size() <= 1) {
            sequence.setStatus(OutreachSequence.STATUS_COMPLETED);
        }

        return outreachSequenceRepository.save(sequence);
    }

    public List<Map<String, Object>> listForRecruiter(Long recruiterId) {
        return outreachSequenceRepository.findByRecruiterIdOrderByCreatedAtDesc(recruiterId).stream()
            .map(this::toView)
            .toList();
    }

    public void stopSequence(Long recruiterId, Long sequenceId) {
        OutreachSequence sequence = outreachSequenceRepository.findById(sequenceId)
            .filter(s -> s.getRecruiterId().equals(recruiterId))
            .orElseThrow(() -> new SequenceNotFoundException(sequenceId));
        sequence.setStatus(OutreachSequence.STATUS_STOPPED);
        outreachSequenceRepository.save(sequence);
    }

    public void processDueSteps() {
        for (OutreachSequence sequence : outreachSequenceRepository.findByStatus(OutreachSequence.STATUS_ACTIVE)) {
            try {
                processOne(sequence);
            } catch (Exception e) {
                log.warn("Outreach sequence {} failed to advance: {}", sequence.getId(), e.getMessage());
            }
        }
    }

    private void processOne(OutreachSequence sequence) {
        if (hasCandidateReplied(sequence)) {
            sequence.setStatus(OutreachSequence.STATUS_REPLIED);
            outreachSequenceRepository.save(sequence);
            return;
        }

        List<Map<String, Object>> steps = deserialize(sequence.getSteps());
        int nextIndex = sequence.getCurrentStep();
        if (nextIndex >= steps.size()) {
            sequence.setStatus(OutreachSequence.STATUS_COMPLETED);
            outreachSequenceRepository.save(sequence);
            return;
        }

        Map<String, Object> step = steps.get(nextIndex);
        LocalDateTime due = sequence.getLastStepSentAt().plusDays(asInt(step.get("delayDays")));
        if (LocalDateTime.now().isBefore(due)) {
            return;
        }

        Recruiter recruiter = recruiterRepository.findById(sequence.getRecruiterId()).orElse(null);
        if (recruiter != null) {
            sendStepMessage(recruiter, sequence.getCandidateUserId(), step);
        }

        sequence.setCurrentStep(nextIndex + 1);
        sequence.setLastStepSentAt(LocalDateTime.now());
        if (nextIndex + 1 >= steps.size()) {
            sequence.setStatus(OutreachSequence.STATUS_COMPLETED);
        }
        outreachSequenceRepository.save(sequence);
    }

    private void sendStepMessage(Recruiter recruiter, Long candidateUserId, Map<String, Object> step) {
        Object messageObj = step.get("message");
        String message = messageObj == null ? "" : messageObj.toString();
        Long senderId = recruiter.getUser() != null ? recruiter.getUser().getId() : null;
        if (senderId != null && !message.isBlank()) {
            directMessageService.sendMessage(recruiter.getId(), candidateUserId, senderId, message);
        }
    }

    private boolean hasCandidateReplied(OutreachSequence sequence) {
        if (sequence.getLastStepSentAt() == null) {
            return false;
        }
        return directMessageRepository
            .findByRecruiterIdAndCandidateUserIdOrderBySentAtAsc(sequence.getRecruiterId(), sequence.getCandidateUserId())
            .stream()
            .anyMatch(message -> message.getSenderId().equals(sequence.getCandidateUserId())
                && message.getSentAt() != null && message.getSentAt().isAfter(sequence.getLastStepSentAt()));
    }

    private Map<String, Object> toView(OutreachSequence sequence) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", sequence.getId());
        view.put("candidateUserId", sequence.getCandidateUserId());
        view.put("candidateName", userRepository.findById(sequence.getCandidateUserId())
            .map(User::getFullName).orElse("Unknown"));
        view.put("status", sequence.getStatus());
        view.put("currentStep", sequence.getCurrentStep());
        view.put("totalSteps", deserialize(sequence.getSteps()).size());
        view.put("createdAt", sequence.getCreatedAt());
        view.put("lastStepSentAt", sequence.getLastStepSentAt());
        return view;
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private String serialize(List<Map<String, Object>> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (Exception e) {
            throw new InvalidStepsException();
        }
    }

    private List<Map<String, Object>> deserialize(String stepsJson) {
        try {
            return objectMapper.readValue(stepsJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
