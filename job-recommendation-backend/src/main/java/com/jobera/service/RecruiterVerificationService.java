package com.jobera.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.jobera.entity.Recruiter;
import com.jobera.repository.RecruiterRepository;

/**
 * "Verified Employer": a recruiter uploads proof of their business (a registration certificate,
 * a company ID) and an admin reviews it - the same PENDING/APPROVED-or-REJECTED moderation
 * lifecycle CompanyReviewService and job-posting moderation already use, applied to the company
 * account itself instead of one piece of content. Gives a candidate a signal that a job posting
 * is from a real, checked business, not just free-text a recruiter typed in.
 */
@Service
public class RecruiterVerificationService {

    private static final String UPLOAD_DIR = "uploads/verifications/";

    public static class RecruiterNotFoundException extends RuntimeException {
        public RecruiterNotFoundException(Long recruiterId) {
            super("Recruiter not found: " + recruiterId);
        }
    }

    public static class NotTheAccountOwnerException extends RuntimeException {
        public NotTheAccountOwnerException() {
            super("Only the account owner can submit verification documents");
        }
    }

    public static class MissingDocumentException extends RuntimeException {
        public MissingDocumentException() {
            super("A verification document file is required");
        }
    }

    public static class DocumentUploadException extends RuntimeException {
        public DocumentUploadException(String message) {
            super(message);
        }
    }

    public static class DocumentNotFoundException extends RuntimeException {
        public DocumentNotFoundException(Long recruiterId) {
            super("No verification document on file for recruiter " + recruiterId);
        }
    }

    private final RecruiterRepository recruiterRepository;

    public RecruiterVerificationService(RecruiterRepository recruiterRepository) {
        this.recruiterRepository = recruiterRepository;
    }

    private Recruiter requireRecruiter(Long recruiterId) {
        return recruiterRepository.findById(recruiterId)
            .orElseThrow(() -> new RecruiterNotFoundException(recruiterId));
    }

    public Recruiter submit(Long recruiterId, Long callerId, MultipartFile file) {
        Recruiter recruiter = requireRecruiter(recruiterId);
        if (recruiter.getUser() == null || !recruiter.getUser().getId().equals(callerId)) {
            throw new NotTheAccountOwnerException();
        }
        if (file == null || file.isEmpty()) {
            throw new MissingDocumentException();
        }

        String storedPath = storeFile(recruiterId, file);
        recruiter.setVerificationDocumentPath(storedPath);
        recruiter.setVerificationStatus(Recruiter.VERIFICATION_PENDING);
        recruiter.setVerificationRejectionReason(null);
        return recruiterRepository.save(recruiter);
    }

    private String storeFile(Long recruiterId, MultipartFile file) {
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR).toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String fileName = recruiterId + "_" + System.currentTimeMillis() + "_"
                + UUID.randomUUID().toString().substring(0, 8) + extension;
            Files.copy(file.getInputStream(), uploadPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/verifications/" + fileName;
        } catch (IOException e) {
            throw new DocumentUploadException("Failed to save the verification document: " + e.getMessage());
        }
    }

    /** Resolves the stored document to an actual file on disk - never served as a static public
     *  URL (see WebConfig's comment on why only profile pictures are), only through
     *  AdminVerificationController's authenticated, admin-only download endpoint. */
    public Path getDocumentFile(Long recruiterId) {
        Recruiter recruiter = requireRecruiter(recruiterId);
        String storedPath = recruiter.getVerificationDocumentPath();
        if (storedPath == null || storedPath.isBlank()) {
            throw new DocumentNotFoundException(recruiterId);
        }
        String fileName = storedPath.substring(storedPath.lastIndexOf('/') + 1);
        Path filePath = Paths.get(UPLOAD_DIR).resolve(fileName).toAbsolutePath().normalize();
        if (!Files.exists(filePath)) {
            throw new DocumentNotFoundException(recruiterId);
        }
        return filePath;
    }

    public Map<String, Object> getStatus(Long recruiterId) {
        Recruiter recruiter = requireRecruiter(recruiterId);
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("verificationStatus", recruiter.getVerificationStatus());
        status.put("verificationRejectionReason", recruiter.getVerificationRejectionReason());
        status.put("hasSubmittedDocument", recruiter.getVerificationDocumentPath() != null);
        return status;
    }

    /** A clean view rather than the raw entity - Recruiter.user carries the full User row
     *  (including the password hash), which has no business leaving this service. */
    public List<Map<String, Object>> getPending() {
        return recruiterRepository.findByVerificationStatus(Recruiter.VERIFICATION_PENDING).stream()
            .map(this::pendingView)
            .toList();
    }

    private Map<String, Object> pendingView(Recruiter recruiter) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("recruiterId", recruiter.getId());
        view.put("companyName", recruiter.getCompanyName());
        view.put("industry", recruiter.getIndustry());
        view.put("companyWebsite", recruiter.getCompanyWebsite());
        view.put("verificationDocumentPath", recruiter.getVerificationDocumentPath());
        return view;
    }

    public Recruiter approve(Long recruiterId) {
        Recruiter recruiter = requireRecruiter(recruiterId);
        recruiter.setVerificationStatus(Recruiter.VERIFICATION_VERIFIED);
        recruiter.setVerificationRejectionReason(null);
        return recruiterRepository.save(recruiter);
    }

    public Recruiter reject(Long recruiterId, String reason) {
        Recruiter recruiter = requireRecruiter(recruiterId);
        recruiter.setVerificationStatus(Recruiter.VERIFICATION_REJECTED);
        recruiter.setVerificationRejectionReason(reason);
        return recruiterRepository.save(recruiter);
    }
}
