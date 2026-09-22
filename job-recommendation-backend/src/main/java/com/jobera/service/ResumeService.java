package com.jobera.service;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.jobera.entity.Resume;
import com.jobera.entity.User;
import com.jobera.repository.ResumeRepository;
import com.jobera.repository.UserRepository;

@Service
public class ResumeService {
    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    /** Older resumes beyond this many per user are pruned (DB row + physical file) after each upload. */
    private static final int MAX_RESUMES_PER_USER = 5;

    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final ResumeProcessingService resumeProcessingService;
    
    public ResumeService(ResumeRepository resumeRepository, 
                        UserRepository userRepository, 
                        ResumeProcessingService resumeProcessingService) {
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.resumeProcessingService = resumeProcessingService;
    }
    
    public Resume processAndSaveResume(Long userId, MultipartFile file) throws ResumeProcessingException {
        log.debug("=== PROCESSING AND SAVING RESUME ===");
        log.debug("User ID: " + userId);
        log.debug("File Name: " + file.getOriginalFilename());
        log.debug("File Size: " + file.getSize() + " bytes");
        
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty()) {
            log.error("❌ User not found with ID: " + userId);
            throw new ResumeProcessingException("User not found with ID: " + userId);
        }
        
        // Ensure uploads directory exists
        java.nio.file.Path uploadsDir = Paths.get("uploads");
        try {
            Files.createDirectories(uploadsDir);
            log.debug("✅ Uploads directory: " + uploadsDir.toAbsolutePath());
        } catch (IOException e) {
            log.error("❌ Failed to create uploads directory: " + e.getMessage());
            throw new ResumeProcessingException("Failed to create uploads directory");
        }
        
        String textContent;
        String fileExtension = getFileExtension(file.getOriginalFilename());
        String savedFileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        java.nio.file.Path filePath = uploadsDir.resolve(savedFileName);
        
        try {
            // Save the file physically
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("✅ File saved to: " + filePath.toAbsolutePath());
            
            if ("pdf".equalsIgnoreCase(fileExtension)) {
                log.debug("📄 Processing PDF file...");
                textContent = resumeProcessingService.extractTextFromPdf(file);
            } else if ("txt".equalsIgnoreCase(fileExtension)) {
                log.debug("📄 Processing text file...");
                textContent = resumeProcessingService.extractTextFromTextFile(file);
            } else {
                log.debug("📄 Processing as text file...");
                textContent = resumeProcessingService.extractTextFromTextFile(file);
            }
        } catch (IOException e) {
            log.error("❌ Failed to save/process file: " + e.getMessage());
            throw new ResumeProcessingException("Failed to save/process file: " + e.getMessage(), e);
        }
        
        log.debug("✅ Text extraction successful. Content length: " + textContent.length() + " characters");
        
        // Parse resume content
        Map<String, Object> parsedData = resumeProcessingService.parseResumeContent(textContent);
        
        // Create and populate resume entity
        Resume resume = new Resume();
        resume.setUser(user.get());
        resume.setFileName(file.getOriginalFilename());
        resume.setFilePath("/uploads/" + savedFileName); // Store relative path for web access
        resume.setParsedText(textContent);
        
        try {
            @SuppressWarnings("unchecked")
            Set<String> skills = (Set<String>) parsedData.get("skills");
            String skillsJson = resumeProcessingService.convertSkillsToJson(skills);
            resume.setSkills(skillsJson);
            log.debug("✅ Extracted " + skills.size() + " skills from resume");
        } catch (Exception e) {
            log.error("❌ Failed to process skills: " + e.getMessage());
            throw new ResumeProcessingException("Failed to process skills: " + e.getMessage(), e);
        }
        
        // Set experience and education
        Integer experienceYears = (Integer) parsedData.get("experienceYears");
        String educationLevel = (String) parsedData.get("educationLevel");
        
        resume.setExperienceYears(experienceYears);
        resume.setEducationLevel(educationLevel);
        
        log.debug("✅ Resume analysis completed:");
        log.debug("   - Experience: " + experienceYears + " years");
        log.debug("   - Education: " + educationLevel);
        
        // Save resume to database
        Resume savedResume = resumeRepository.save(resume);
        log.debug("✅ Resume saved successfully with ID: " + savedResume.getId());

        pruneOldResumes(userId);

        return savedResume;
    }

    /** Keeps only the {@value #MAX_RESUMES_PER_USER} most recent resumes for a user; older ones are deleted (DB + disk). */
    void pruneOldResumes(Long userId) {
        List<Resume> oldestFirst = resumeRepository.findByUserIdOrderByUploadDateAsc(userId);
        int excess = oldestFirst.size() - MAX_RESUMES_PER_USER;
        for (int i = 0; i < excess; i++) {
            deleteResume(oldestFirst.get(i).getId());
        }
    }
    
    public List<Resume> getResumesByUserId(Long userId) {
        try {
            log.debug("📋 Fetching resumes for user ID: " + userId);
            List<Resume> resumes = resumeRepository.findByUserId(userId);
            log.debug("✅ Found " + resumes.size() + " resumes for user " + userId);
            return resumes;
        } catch (Exception e) {
            log.error("❌ Error fetching resumes for user " + userId + ": " + e.getMessage());
            return List.of();
        }
    }
    
    // FIXED: Enhanced getLatestResumeByUserId with better error handling
    public Optional<Resume> getLatestResumeByUserId(Long userId) {
        try {
            log.debug("📋 Fetching latest resume for user ID: " + userId);
            
            // First check if user exists
            Optional<User> user = userRepository.findById(userId);
            if (user.isEmpty()) {
                log.error("❌ User not found with ID: " + userId);
                return Optional.empty();
            }
            
            List<Resume> resumes = resumeRepository.findByUserId(userId);
            log.debug("✅ Found " + resumes.size() + " total resumes for user " + userId);
            
            if (resumes.isEmpty()) {
                log.debug("ℹ️ No resume found for user " + userId);
                return Optional.empty();
            }
            
            // Sort by upload date descending and get the first one
            Optional<Resume> latestResume = resumes.stream()
                .sorted((r1, r2) -> r2.getUploadDate().compareTo(r1.getUploadDate()))
                .findFirst();
            
            if (latestResume.isPresent()) {
                Resume resume = latestResume.get();
                log.debug("✅ Found latest resume with ID: " + resume.getId());
                log.debug("   Skills: " + resume.getSkills());
                log.debug("   Experience: " + resume.getExperienceYears());
                log.debug("   Education: " + resume.getEducationLevel());
            }
            
            return latestResume;
            
        } catch (Exception e) {
            log.error("❌ Error fetching latest resume for user " + userId + ": " + e.getMessage());
            log.error("Unexpected error", e);
            return Optional.empty();
        }
    }
    
    public Optional<Resume> getResumeById(Long resumeId) {
        try {
            log.debug("📋 Fetching resume by ID: " + resumeId);
            Optional<Resume> resume = resumeRepository.findById(resumeId);
            if (resume.isPresent()) {
                log.debug("✅ Found resume with ID: " + resumeId);
            } else {
                log.error("❌ Resume not found with ID: " + resumeId);
            }
            return resume;
        } catch (Exception e) {
            log.error("❌ Error fetching resume by ID " + resumeId + ": " + e.getMessage());
            return Optional.empty();
        }
    }
    
    public Map<String, Object> analyzeResume(Long resumeId) throws ResumeProcessingException {
        log.debug("🔍 Analyzing resume with ID: " + resumeId);
        
        Optional<Resume> resume = resumeRepository.findById(resumeId);
        if (resume.isPresent()) {
            Resume resumeEntity = resume.get();
            log.debug("✅ Resume found: " + resumeEntity.getFileName());
            log.debug("📝 Parsing resume content...");
            
            Map<String, Object> analysis = resumeProcessingService.parseResumeContent(resumeEntity.getParsedText());
            log.debug("✅ Resume analysis completed for ID: " + resumeId);
            
            return analysis;
        }
        
        log.error("❌ Resume not found with ID: " + resumeId);
        throw new ResumeProcessingException("Resume not found with ID: " + resumeId);
    }
    
    @SuppressWarnings("unchecked")
    public Set<String> extractSkillsFromText(String text) {
        try {
            log.debug("🔧 Extracting skills from text (length: " + text.length() + " characters)");
            Map<String, Object> parsedData = resumeProcessingService.parseResumeContent(text);
            Set<String> skills = (Set<String>) parsedData.get("skills");
            log.debug("✅ Extracted " + skills.size() + " skills from text");
            return skills;
        } catch (Exception e) {
            log.error("❌ Error extracting skills from text: " + e.getMessage());
            return Set.of();
        }
    }
    
    public boolean deleteResume(Long resumeId) {
        try {
            log.debug("🗑️ Deleting resume with ID: " + resumeId);
            
            Optional<Resume> resume = resumeRepository.findById(resumeId);
            if (resume.isPresent()) {
                // Also delete the physical file
                Resume resumeEntity = resume.get();
                if (resumeEntity.getFilePath() != null && !resumeEntity.getFilePath().isEmpty()) {
                    try {
                        java.nio.file.Path filePath = Paths.get(resumeEntity.getFilePath().replace("/uploads/", "uploads/"));
                        if (Files.exists(filePath)) {
                            Files.delete(filePath);
                            log.debug("✅ Physical file deleted: " + filePath);
                        }
                    } catch (IOException e) {
                        log.warn("⚠️ Could not delete physical file: " + e.getMessage());
                    }
                }
                
                resumeRepository.delete(resumeEntity);
                log.debug("✅ Resume deleted successfully: " + resumeId);
                return true;
            } else {
                log.error("❌ Resume not found for deletion: " + resumeId);
                return false;
            }
        } catch (Exception e) {
            log.error("❌ Error deleting resume " + resumeId + ": " + e.getMessage());
            return false;
        }
    }
    
    public List<Resume> getAllResumes() {
        try {
            log.debug("📋 Fetching all resumes");
            List<Resume> resumes = resumeRepository.findAll();
            log.debug("✅ Found " + resumes.size() + " resumes in total");
            return resumes;
        } catch (Exception e) {
            log.error("❌ Error fetching all resumes: " + e.getMessage());
            return List.of();
        }
    }
    
    public boolean userHasResume(Long userId) {
        try {
            log.debug("🔍 Checking if user " + userId + " has resumes");
            List<Resume> resumes = resumeRepository.findByUserId(userId);
            boolean hasResume = !resumes.isEmpty();
            log.debug("✅ User " + userId + " has resumes: " + hasResume);
            return hasResume;
        } catch (Exception e) {
            log.error("❌ Error checking if user has resume: " + e.getMessage());
            return false;
        }
    }
    
    public int getResumeCountByUserId(Long userId) {
        try {
            log.debug("📊 Getting resume count for user: " + userId);
            List<Resume> resumes = resumeRepository.findByUserId(userId);
            int count = resumes.size();
            log.debug("✅ User " + userId + " has " + count + " resumes");
            return count;
        } catch (Exception e) {
            log.error("❌ Error getting resume count: " + e.getMessage());
            return 0;
        }
    }
    
    // NEW METHOD: Fix file paths for existing resumes
    public boolean fixResumeFilePath(Long resumeId) {
        try {
            log.debug("🔧 Fixing file path for resume ID: " + resumeId);
            
            Optional<Resume> resumeOpt = resumeRepository.findById(resumeId);
            if (resumeOpt.isEmpty()) {
                log.error("❌ Resume not found: " + resumeId);
                return false;
            }
            
            Resume resume = resumeOpt.get();
            String currentPath = resume.getFilePath();
            
            if (currentPath == null || currentPath.isEmpty()) {
                log.error("❌ No file path to fix");
                return false;
            }
            
            // Check if file exists at current path
            File currentFile = new File(currentPath);
            if (currentFile.exists()) {
                log.debug("✅ File already exists at: " + currentPath);
                return true;
            }
            
            // Try to find the file in uploads directory
            String fileName = new File(currentPath).getName();
            java.nio.file.Path uploadsDir = Paths.get("uploads");
            java.nio.file.Path possiblePath = uploadsDir.resolve(fileName);
            
            if (Files.exists(possiblePath)) {
                // Update with correct path
                resume.setFilePath("/uploads/" + fileName);
                resumeRepository.save(resume);
                log.debug("✅ Fixed file path to: /uploads/" + fileName);
                return true;
            }
            
            log.error("❌ Could not find file for resume: " + resumeId);
            return false;
            
        } catch (Exception e) {
            log.error("❌ Error fixing resume file path: " + e.getMessage());
            return false;
        }
    }
    
    // NEW METHOD: Get physical file path for resume
    public java.nio.file.Path getPhysicalFilePath(Long resumeId) {
        try {
            Optional<Resume> resumeOpt = resumeRepository.findById(resumeId);
            if (resumeOpt.isPresent()) {
                Resume resume = resumeOpt.get();
                String filePath = resume.getFilePath();
                
                if (filePath != null && !filePath.isEmpty()) {
                    // Convert web path to physical path
                    if (filePath.startsWith("/uploads/")) {
                        return Paths.get("uploads", filePath.substring("/uploads/".length()));
                    } else if (filePath.startsWith("uploads/")) {
                        return Paths.get(filePath);
                    } else {
                        return Paths.get("uploads", filePath);
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ Error getting physical file path: " + e.getMessage());
        }
        return null;
    }
    
    // NEW METHOD: Check if physical file exists
    public boolean physicalFileExists(Long resumeId) {
        try {
            java.nio.file.Path physicalPath = getPhysicalFilePath(resumeId);
            return physicalPath != null && Files.exists(physicalPath);
        } catch (Exception e) {
            log.error("❌ Error checking physical file existence: " + e.getMessage());
            return false;
        }
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
    
    public static class ResumeProcessingException extends Exception {
        public ResumeProcessingException(String message) {
            super(message);
        }
        
        public ResumeProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}