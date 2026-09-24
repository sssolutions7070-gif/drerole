package com.jobera.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.jobera.entity.JobOffer;

@Repository
public interface JobOfferRepository extends JpaRepository<JobOffer, Long> {
    Optional<JobOffer> findByApplicationId(Long applicationId);
}
