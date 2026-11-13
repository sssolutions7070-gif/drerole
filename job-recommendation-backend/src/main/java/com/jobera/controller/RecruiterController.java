package com.jobera.controller;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.jobera.entity.Recruiter;
import com.jobera.service.DiversityAnalyticsService;
import com.jobera.service.RecruiterService;

@RestController
@RequestMapping("/api/recruiters")
public class RecruiterController {
    private static final Logger log = LoggerFactory.getLogger(RecruiterController.class);


    private final RecruiterService recruiterService;
    private final DiversityAnalyticsService diversityAnalyticsService;

    @org.springframework.beans.factory.annotation.Autowired
    public RecruiterController(RecruiterService recruiterService, DiversityAnalyticsService diversityAnalyticsService) {
        this.recruiterService = recruiterService;
        this.diversityAnalyticsService = diversityAnalyticsService;
    }

    public RecruiterController(RecruiterService recruiterService) {
        this(recruiterService, null);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerRecruiter(@RequestBody Map<String, Object> request) {
        try {
            Long userId = Long.valueOf(request.get("userId").toString());
            String companyName = (String) request.get("companyName");
            String companyDescription = (String) request.get("companyDescription");
            String companyWebsite = (String) request.get("companyWebsite");
            String companySize = (String) request.get("companySize");
            String industry = (String) request.get("industry");
            String contactEmail = (String) request.get("contactEmail");
            String contactPhone = (String) request.get("contactPhone");
            
            Recruiter recruiter = recruiterService.registerRecruiter(
                userId, companyName, companyDescription, companyWebsite, 
                companySize, industry, contactEmail, contactPhone
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Recruiter registered successfully");
            response.put("recruiter", createRecruiterResponse(recruiter));
            
            // Include user info with updated role
            if (recruiter.getUser() != null) {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", recruiter.getUser().getId());
                userInfo.put("email", recruiter.getUser().getEmail());
                userInfo.put("fullName", recruiter.getUser().getFullName());
                userInfo.put("role", recruiter.getUser().getRole());
                userInfo.put("roleId", recruiter.getUser().getRoleId());
                response.put("user", userInfo);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (RecruiterService.RecruiterException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error", e); // Add logging for debugging
            return ResponseEntity.badRequest().body(Map.of("error", "Registration failed: " + e.getMessage()));
        }
    }
    
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getRecruiterByUser(@PathVariable Long userId) {
        try {
            Optional<Recruiter> recruiter = recruiterService.getRecruiterByUserId(userId);
            if (recruiter.isPresent()) {
                Map<String, Object> response = new HashMap<>();
                response.put("recruiter", createRecruiterResponse(recruiter.get()));
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.ok(Map.of("recruiter", null, "message", "No recruiter profile found"));
            }
        } catch (Exception e) {
            log.error("Unexpected error", e); // Add logging for debugging
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to fetch recruiter profile: " + e.getMessage()));
        }
    }

    @GetMapping("/{recruiterId}/diversity-analytics")
    public ResponseEntity<Map<String, Object>> getDiversityAnalytics(@PathVariable Long recruiterId) {
        return ResponseEntity.ok(diversityAnalyticsService.getAggregateForRecruiter(recruiterId));
    }
    
    @PutMapping("/user/{userId}/profile")
    public ResponseEntity<Map<String, Object>> updateRecruiterProfile(
            @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        try {
            Integer foundedYear = null;
            if (request.get("foundedYear") != null && !request.get("foundedYear").isBlank()) {
                try {
                    foundedYear = Integer.valueOf(request.get("foundedYear"));
                } catch (NumberFormatException ignored) {
                    // Leave foundedYear unset rather than fail the whole profile update
                }
            }

            Recruiter recruiter = recruiterService.updateRecruiterProfile(
                userId,
                request.get("companyDescription"),
                request.get("companyWebsite"),
                request.get("companySize"),
                request.get("industry"),
                request.get("contactEmail"),
                request.get("contactPhone"),
                request.get("missionStatement"),
                request.get("benefits"),
                request.get("companyLinkedin"),
                request.get("companyTwitter"),
                foundedYear,
                request.get("headquarters")
            );

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Profile updated successfully");
            response.put("recruiter", createRecruiterResponse(recruiter));

            return ResponseEntity.ok(response);

        } catch (RecruiterService.RecruiterException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error", e); // Add logging for debugging
            return ResponseEntity.badRequest().body(Map.of("error", "Profile update failed: " + e.getMessage()));
        }
    }

    private static final String COMPANY_LOGO_DIR = "uploads/company-logos/";
    private static final String COMPANY_BANNER_DIR = "uploads/company-banners/";

    @PostMapping("/user/{userId}/logo")
    public ResponseEntity<Map<String, String>> uploadCompanyLogo(@PathVariable Long userId,
                                                                  @RequestParam("file") MultipartFile file) {
        try {
            String fileUrl = storeBrandingImage(userId, file, COMPANY_LOGO_DIR, "/uploads/company-logos/");
            recruiterService.updateCompanyLogo(userId, fileUrl);
            return ResponseEntity.ok(Map.of("message", "Company logo uploaded successfully", "companyLogo", fileUrl));
        } catch (RecruiterService.RecruiterException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("Error uploading company logo: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to upload logo: " + e.getMessage()));
        }
    }

    @PostMapping("/user/{userId}/banner")
    public ResponseEntity<Map<String, String>> uploadCompanyBanner(@PathVariable Long userId,
                                                                     @RequestParam("file") MultipartFile file) {
        try {
            String fileUrl = storeBrandingImage(userId, file, COMPANY_BANNER_DIR, "/uploads/company-banners/");
            recruiterService.updateCompanyBanner(userId, fileUrl);
            return ResponseEntity.ok(Map.of("message", "Company banner uploaded successfully", "companyBanner", fileUrl));
        } catch (RecruiterService.RecruiterException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            log.error("Error uploading company banner: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", "Failed to upload banner: " + e.getMessage()));
        }
    }

    private String storeBrandingImage(Long userId, MultipartFile file, String uploadDir, String publicPrefix)
            throws IOException {
        if (file.isEmpty()) {
            throw new RecruiterService.RecruiterException("File is empty");
        }
        Path uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String fileName = userId + "_" + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + extension;
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return publicPrefix + fileName;
    }
    
    private Map<String, Object> createRecruiterResponse(Recruiter recruiter) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", recruiter.getId());
        response.put("companyName", recruiter.getCompanyName());
        response.put("companyDescription", recruiter.getCompanyDescription());
        response.put("companyWebsite", recruiter.getCompanyWebsite());
        response.put("companySize", recruiter.getCompanySize());
        response.put("industry", recruiter.getIndustry());
        response.put("contactEmail", recruiter.getContactEmail());
        response.put("contactPhone", recruiter.getContactPhone());
        response.put("companyLogo", recruiter.getCompanyLogo());
        response.put("companyBanner", recruiter.getCompanyBanner());
        response.put("missionStatement", recruiter.getMissionStatement());
        response.put("benefits", recruiter.getBenefits());
        response.put("companyLinkedin", recruiter.getCompanyLinkedin());
        response.put("companyTwitter", recruiter.getCompanyTwitter());
        response.put("foundedYear", recruiter.getFoundedYear());
        response.put("headquarters", recruiter.getHeadquarters());
        response.put("createdAt", recruiter.getCreatedAt());
        response.put("updatedAt", recruiter.getUpdatedAt());
        
        // Include user info
        if (recruiter.getUser() != null) {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", recruiter.getUser().getId());
            userInfo.put("email", recruiter.getUser().getEmail());
            userInfo.put("fullName", recruiter.getUser().getFullName());
            userInfo.put("phone", recruiter.getUser().getPhone());
            userInfo.put("role", recruiter.getUser().getRole());
            userInfo.put("roleId", recruiter.getUser().getRoleId());
            response.put("user", userInfo);
        }
        
        return response;
    }
}