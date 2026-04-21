package com.anju.domain.property.dto;

import com.anju.domain.property.Property;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public class PropertyResponse {

    private Long id;
    private String code;
    private String status;
    private BigDecimal rent;
    private BigDecimal deposit;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String complianceStatus;
    private List<String> mediaRefs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PropertyResponse fromEntity(Property property) {
        PropertyResponse response = new PropertyResponse();
        response.setId(property.getId());
        response.setCode(property.getCode());
        response.setStatus(property.getStatus());
        response.setRent(property.getRent());
        response.setDeposit(property.getDeposit());
        response.setStartDate(property.getStartDate());
        response.setEndDate(property.getEndDate());
        response.setComplianceStatus(property.getComplianceStatus());
        response.setMediaRefs(parseMediaRefs(property.getMediaRefs()));
        response.setCreatedAt(property.getCreatedAt());
        response.setUpdatedAt(property.getUpdatedAt());
        return response;
    }

    private static List<String> parseMediaRefs(String rawMediaRefs) {
        if (rawMediaRefs == null || rawMediaRefs.isBlank()) {
            return Collections.emptyList();
        }
        return java.util.Arrays.stream(rawMediaRefs.split("\\n"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getRent() {
        return rent;
    }

    public void setRent(BigDecimal rent) {
        this.rent = rent;
    }

    public BigDecimal getDeposit() {
        return deposit;
    }

    public void setDeposit(BigDecimal deposit) {
        this.deposit = deposit;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public String getComplianceStatus() {
        return complianceStatus;
    }

    public void setComplianceStatus(String complianceStatus) {
        this.complianceStatus = complianceStatus;
    }

    public List<String> getMediaRefs() {
        return mediaRefs;
    }

    public void setMediaRefs(List<String> mediaRefs) {
        this.mediaRefs = mediaRefs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
