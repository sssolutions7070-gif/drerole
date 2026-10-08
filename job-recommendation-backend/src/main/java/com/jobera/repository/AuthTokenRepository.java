package com.jobera.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import com.jobera.entity.AuthToken;

@Repository
public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    Optional<AuthToken> findByToken(String token);

    List<AuthToken> findByUserIdAndExpiryDateAfterOrderByLastUsedAtDesc(Long userId, LocalDateTime now);

    Optional<AuthToken> findByIdAndUserId(Long id, Long userId);

    @Transactional
    void deleteByToken(String token);

    @Transactional
    void deleteByUserId(Long userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM AuthToken t WHERE t.expiryDate < :now")
    int deleteExpired(@Param("now") LocalDateTime now);

    @Transactional
    @Modifying
    @Query("DELETE FROM AuthToken t WHERE LENGTH(t.token) <> 64")
    int deleteUnhashed();
}