// File: src/main/java/com/jobera/repository/TestAssignmentRepository.java
package com.jobera.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.jobera.entity.TestAssignment;

@Repository
public interface TestAssignmentRepository extends JpaRepository<TestAssignment, Long> {
    List<TestAssignment> findByApplicationId(Long applicationId);

    // face_verifications.assignment_id is a hard FK to this table left over from an old,
    // now-unused feature (no entity anywhere in this codebase maps it), but the constraint is
    // still live in the database - deleteByApplicationId would fail if it were ever populated.
    @Transactional
    @Modifying
    @Query(value = "DELETE FROM face_verifications WHERE assignment_id = :assignmentId", nativeQuery = true)
    void deleteFaceVerificationsByAssignmentId(@Param("assignmentId") Long assignmentId);

    @Transactional
    void deleteByApplicationId(Long applicationId);
    List<TestAssignment> findByTestId(Long testId);
    Optional<TestAssignment> findByApplicationIdAndTestId(Long applicationId, Long testId);
    
    @Query("SELECT ta FROM TestAssignment ta WHERE ta.application.jobPosting.id = :jobId")
    List<TestAssignment> findByJobId(@Param("jobId") Long jobId);
    
    @Query("SELECT ta FROM TestAssignment ta WHERE ta.application.jobPosting.id = :jobId AND ta.passed = true")
    List<TestAssignment> findPassedByJobId(@Param("jobId") Long jobId);
    
    @Query("SELECT ta FROM TestAssignment ta WHERE ta.test.recruiter.id = :recruiterId")
    List<TestAssignment> findByRecruiterId(@Param("recruiterId") Long recruiterId);
    
    @Query("SELECT AVG(ta.score) FROM TestAssignment ta WHERE ta.test.id = :testId AND ta.status = 'COMPLETED'")
    Double getAverageScoreForTest(@Param("testId") Long testId);
}