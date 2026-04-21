package com.anju.domain.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SysFileRepository extends JpaRepository<SysFile, Long> {

    Optional<SysFile> findByHash(String hash);

    Optional<SysFile> findTopByHashAndIsDeletedFalseOrderByVersionDesc(String hash);

    Optional<SysFile> findByHashAndVersionAndIsDeletedFalse(String hash, Integer version);

    Optional<SysFile> findByIdAndIsDeletedFalse(Long id);

    Optional<SysFile> findByIdAndIsDeletedTrue(Long id);

    List<SysFile> findByHashAndIsDeletedFalseOrderByVersionDesc(String hash);

    List<SysFile> findByIsDeletedTrueOrderByUpdatedAtDesc();

    List<SysFile> findByUploadedByAndIsDeletedTrueOrderByUpdatedAtDesc(Long uploadedBy);

    List<SysFile> findByUploadedByAndIsDeletedFalseOrderByUpdatedAtDesc(Long uploadedBy);

    @Modifying
    @Query("DELETE FROM SysFile f WHERE f.isDeleted = true AND f.updatedAt < :thresholdDate")
    int permanentlyDeleteOldFiles(@Param("thresholdDate") LocalDateTime thresholdDate);

    @Modifying
    @Query("DELETE FROM SysFile f WHERE f.expiresAt IS NOT NULL AND f.expiresAt < :thresholdDate")
    int permanentlyDeleteExpiredFiles(@Param("thresholdDate") LocalDateTime thresholdDate);
}
