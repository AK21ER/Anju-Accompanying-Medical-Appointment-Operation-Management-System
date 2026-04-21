package com.anju.domain.file;

import com.anju.domain.file.dto.FileUploadRequest;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import com.anju.common.BusinessException;
import com.anju.domain.auth.CurrentUserService;
import com.anju.domain.auth.User;
import com.anju.domain.file.dto.ChunkUploadRequest;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private final SysFileRepository sysFileRepository;
    private final CurrentUserService currentUserService;
    private final int maxConcurrentUploadsPerUser;
    private final long minChunkUploadIntervalMs;
    private final int defaultRetentionDays;
    private final Map<Long, AtomicInteger> activeUploadsByUser = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastUploadEpochMsByUser = new ConcurrentHashMap<>();

    public FileService(SysFileRepository sysFileRepository,
                       CurrentUserService currentUserService,
                       @Value("${file.upload.max-concurrent-per-user:3}") int maxConcurrentUploadsPerUser,
                       @Value("${file.upload.min-chunk-interval-ms:150}") long minChunkUploadIntervalMs,
                       @Value("${file.retention.default-days:30}") int defaultRetentionDays) {
        this.sysFileRepository = sysFileRepository;
        this.currentUserService = currentUserService;
        this.maxConcurrentUploadsPerUser = maxConcurrentUploadsPerUser;
        this.minChunkUploadIntervalMs = minChunkUploadIntervalMs;
        this.defaultRetentionDays = defaultRetentionDays;
    }

    @Transactional
    public SysFile checkFastUpload(String hash) {
        User currentUser = currentUserService.requireCurrentUser();
        Optional<SysFile> found = sysFileRepository.findTopByHashAndIsDeletedFalseOrderByVersionDesc(hash);
        if (found.isEmpty()) {
            return null;
        }
        enforceFileAccess(found.get(), currentUser);
        return found.get();
    }

    @Transactional
    public SysFile uploadFile(FileUploadRequest request) {
        User currentUser = currentUserService.requireCurrentUser();
        MultipartFile file = request.getFile();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "File is empty");
        }

        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded_file.pdf";
        String fileHash = request.getHash() != null ? request.getHash() : UUID.randomUUID().toString().replace("-", "");
        
        File uploadDir = new File("uploads");
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
        
        try {
            Path targetPath = uploadDir.toPath().resolve(fileHash + "_" + fileName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            
            SysFile sysFile = new SysFile();
            sysFile.setHash(fileHash);
            sysFile.setChunks(1);
            sysFile.setVersion(1);
            sysFile.setFileName(fileName);
            sysFile.setContentType(file.getContentType() != null ? file.getContentType() : "application/pdf");
            sysFile.setSizeBytes(file.getSize());
            sysFile.setUploadedBy(currentUser.getId());
            sysFile.setStoragePath(targetPath.toAbsolutePath().toString());
            sysFile.setExpiresAt(resolveExpiresAt(request.getExpiresAt()));
            
            return sysFileRepository.save(sysFile);
            
        } catch (IOException e) {
            log.error("Failed to store file: ", e);
            throw new BusinessException(500, "Failed to upload file");
        }
    }

    @Transactional
    public SysFile uploadChunk(ChunkUploadRequest request) {
        validateChunkRequest(request);
        User currentUser = currentUserService.requireCurrentUser();
        acquireUploadPermit(currentUser.getId());

        try {
            Optional<SysFile> existing = sysFileRepository.findTopByHashAndIsDeletedFalseOrderByVersionDesc(request.getHash());
            SysFile sysFile;
            if (existing.isPresent()) {
                sysFile = existing.get();
                enforceFileAccess(sysFile, currentUser);
                if (sysFile.getStoragePath() != null) {
                    return sysFile;
                }
            } else {
                sysFile = new SysFile();
                sysFile.setHash(request.getHash());
                sysFile.setChunks(request.getChunks());
                sysFile.setVersion(1);
                sysFile.setFileName(request.getFileName() == null ? "upload_" + System.currentTimeMillis() : request.getFileName().trim());
                sysFile.setContentType(request.getContentType() == null ? "application/octet-stream" : request.getContentType().trim());
                sysFile.setSizeBytes(request.getSizeBytes() == null ? 0L : request.getSizeBytes());
                sysFile.setUploadedBy(currentUser.getId());
                sysFile.setExpiresAt(resolveExpiresAt(request.getExpiresAt()));
                sysFile = sysFileRepository.save(sysFile);
            }

            if (request.getCurrentChunk().equals(sysFile.getChunks())) {
                sysFile.setStoragePath("/files/" + sysFile.getHash() + "/v" + sysFile.getVersion());
                if (sysFile.getExpiresAt() == null) {
                    sysFile.setExpiresAt(resolveExpiresAt(request.getExpiresAt()));
                }
                sysFileRepository.save(sysFile);
            }

            return sysFile;
        } finally {
            releaseUploadPermit(currentUser.getId());
        }
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public SysFile getFile(Long id) {
        SysFile file = sysFileRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(4040, "File not found."));
        if (file.getExpiresAt() != null && file.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(4044, "File has expired.");
        }
        enforceFileAccess(file, currentUserService.requireCurrentUser());
        return file;
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<SysFile> listAccessibleFiles() {
        User currentUser = currentUserService.requireCurrentUser();
        if (isPrivileged(currentUser)) {
            return sysFileRepository.findAll().stream()
                .filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
                .filter(f -> f.getExpiresAt() == null || f.getExpiresAt().isAfter(LocalDateTime.now()))
                .toList();
        }
        return sysFileRepository.findByUploadedByAndIsDeletedFalseOrderByUpdatedAtDesc(currentUser.getId())
            .stream()
            .filter(f -> f.getExpiresAt() == null || f.getExpiresAt().isAfter(LocalDateTime.now()))
            .toList();
    }

    @Transactional
    public SysFile moveToRecycleBin(Long id) {
        User currentUser = currentUserService.requireCurrentUser();
        SysFile file = sysFileRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(4040, "File not found."));
        enforceFileAccess(file, currentUser);
        file.setIsDeleted(true);
        return sysFileRepository.save(file);
    }

    @Transactional
    public SysFile restoreFromRecycleBin(Long id) {
        User currentUser = currentUserService.requireCurrentUser();
        SysFile file = sysFileRepository.findByIdAndIsDeletedTrue(id)
                .orElseThrow(() -> new BusinessException(4041, "Deleted file not found."));
        enforceFileAccess(file, currentUser);
        file.setIsDeleted(false);
        return sysFileRepository.save(file);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<SysFile> listRecycleBin() {
        User currentUser = currentUserService.requireCurrentUser();
        if (isPrivileged(currentUser)) {
            return sysFileRepository.findByIsDeletedTrueOrderByUpdatedAtDesc();
        }
        return sysFileRepository.findByUploadedByAndIsDeletedTrueOrderByUpdatedAtDesc(currentUser.getId());
    }

    @Transactional
    public SysFile rollbackVersion(String hash, Integer targetVersion) {
        User currentUser = currentUserService.requireCurrentUser();
        SysFile target = sysFileRepository.findByHashAndVersionAndIsDeletedFalse(hash, targetVersion)
                .orElseThrow(() -> new BusinessException(4042, "Target version not found."));
        enforceFileAccess(target, currentUser);

        SysFile latest = sysFileRepository.findTopByHashAndIsDeletedFalseOrderByVersionDesc(hash)
                .orElseThrow(() -> new BusinessException(4043, "No active file version found."));

        SysFile rolled = new SysFile();
        rolled.setHash(target.getHash());
        rolled.setVersion(latest.getVersion() + 1);
        rolled.setChunks(target.getChunks());
        rolled.setFileName(target.getFileName());
        rolled.setContentType(target.getContentType());
        rolled.setSizeBytes(target.getSizeBytes());
        rolled.setStoragePath(target.getStoragePath());
        rolled.setUploadedBy(currentUser.getId());
        rolled.setIsDeleted(false);
        rolled.setExpiresAt(resolveExpiresAt(target.getExpiresAt()));
        return sysFileRepository.save(rolled);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public Map<String, Object> preview(Long id) {
        SysFile file = getFile(id);
        if (file.getContentType() == null) {
            throw new BusinessException(4008, "Preview is not supported for unknown content type.");
        }

        String contentType = file.getContentType().toLowerCase();
        String mode;
        if (contentType.startsWith("image/")) {
            mode = "image";
        } else if (contentType.startsWith("audio/")) {
            mode = "audio";
        } else if (contentType.startsWith("video/")) {
            mode = "video";
        } else if (contentType.contains("pdf")) {
            mode = "pdf";
        } else if (contentType.startsWith("text/")) {
            mode = "text";
        } else if (contentType.contains("word")
                || contentType.contains("excel")
                || contentType.contains("powerpoint")
                || contentType.contains("officedocument")) {
            mode = "document";
        } else {
            throw new BusinessException(4009, "Preview is not supported for this file type.");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fileId", file.getId());
        result.put("mode", mode);
        result.put("previewUrl", "/file/" + file.getId() + "/content");
        return result;
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public Map<String, Object> contentDescriptor(Long id) {
        SysFile file = getFile(id);
        Map<String, Object> result = new HashMap<>();
        result.put("fileId", file.getId());
        result.put("storagePath", file.getStoragePath());
        result.put("contentType", file.getContentType());
        result.put("sizeBytes", file.getSizeBytes());
        return result;
    }

    @Scheduled(cron = "0 0 2 * * ?") // Every day at 2 AM
    @Transactional
    public void recycleBinTask() {
        log.info("Starting recycle bin task...");
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        int deletedCount = sysFileRepository.permanentlyDeleteOldFiles(threshold);
        int expiredCount = sysFileRepository.permanentlyDeleteExpiredFiles(LocalDateTime.now());
        log.info("Recycle bin task completed. Permanently deleted {} deleted files and {} expired files.", deletedCount, expiredCount);
    }

    private void acquireUploadPermit(Long userId) {
        long now = System.currentTimeMillis();
        Long lastUploadAt = lastUploadEpochMsByUser.get(userId);
        if (lastUploadAt != null && now - lastUploadAt < minChunkUploadIntervalMs) {
            throw new BusinessException(4291, "Upload throttled. Please slow down chunk submissions.");
        }

        AtomicInteger active = activeUploadsByUser.computeIfAbsent(userId, ignored -> new AtomicInteger(0));
        int current = active.incrementAndGet();
        if (current > maxConcurrentUploadsPerUser) {
            active.decrementAndGet();
            throw new BusinessException(4290, "Too many concurrent uploads for this user.");
        }
        lastUploadEpochMsByUser.put(userId, now);
    }

    private void releaseUploadPermit(Long userId) {
        AtomicInteger active = activeUploadsByUser.get(userId);
        if (active == null) {
            return;
        }
        if (active.decrementAndGet() <= 0) {
            activeUploadsByUser.remove(userId);
        }
    }

    private LocalDateTime resolveExpiresAt(LocalDateTime requestedExpiresAt) {
        if (requestedExpiresAt != null) {
            return requestedExpiresAt;
        }
        return LocalDateTime.now().plusDays(defaultRetentionDays);
    }

    private void validateChunkRequest(ChunkUploadRequest request) {
        if (request.getHash() == null || request.getHash().isBlank()) {
            throw new BusinessException(4001, "File hash is required.");
        }
        if (request.getChunks() == null || request.getChunks() <= 0 || request.getChunks() > 10000) {
            throw new BusinessException(4002, "Chunk count must be between 1 and 10000.");
        }
        if (request.getCurrentChunk() == null || request.getCurrentChunk() <= 0 || request.getCurrentChunk() > request.getChunks()) {
            throw new BusinessException(4003, "Current chunk index is out of range.");
        }
    }

    private void enforceFileAccess(SysFile file, User user) {
        if (isPrivileged(user)) {
            return;
        }
        if (file.getUploadedBy() == null || !file.getUploadedBy().equals(user.getId())) {
            throw new BusinessException(4034, "You are not allowed to access this file.");
        }
    }

    private boolean isPrivileged(User user) {
        return "ADMIN".equalsIgnoreCase(user.getRole()) || "OPERATOR".equalsIgnoreCase(user.getRole());
    }
}
