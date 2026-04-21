package com.anju.domain.file.dto;

import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;

public class FileUploadRequest {
    private MultipartFile file;
    private String hash;
    private LocalDateTime expiresAt;

    public MultipartFile getFile() { return file; }
    public void setFile(MultipartFile file) { this.file = file; }

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
