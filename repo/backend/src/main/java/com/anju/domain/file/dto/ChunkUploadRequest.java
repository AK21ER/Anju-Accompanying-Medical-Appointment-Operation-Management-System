package com.anju.domain.file.dto;

import java.time.LocalDateTime;

public class ChunkUploadRequest {
    private String hash;
    private String fileName;
    private String contentType;
    private Long sizeBytes;
    private Integer chunks;
    private Integer currentChunk;
    private byte[] chunkData;
    private LocalDateTime expiresAt;

    public String getHash() { return hash; }
    public void setHash(String hash) { this.hash = hash; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public Integer getChunks() { return chunks; }
    public void setChunks(Integer chunks) { this.chunks = chunks; }
    public Integer getCurrentChunk() { return currentChunk; }
    public void setCurrentChunk(Integer currentChunk) { this.currentChunk = currentChunk; }
    public byte[] getChunkData() { return chunkData; }
    public void setChunkData(byte[] chunkData) { this.chunkData = chunkData; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
