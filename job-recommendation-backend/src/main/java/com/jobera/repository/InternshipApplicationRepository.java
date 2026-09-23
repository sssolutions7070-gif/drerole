// src/main/java/com/jobera/repository/InternshipApplicationRepository.java
package com.jobera.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.jobera.entity.InternshipApplication;

@Repository
public interface InternshipApplicationRepository extends JpaRepository<InternshipApplication, Long> {
    boolean existsByUserIdAndInternshipId(Long userId, Long internshipId);

    @Transactional
    void deleteByUserId(Long userId);
}