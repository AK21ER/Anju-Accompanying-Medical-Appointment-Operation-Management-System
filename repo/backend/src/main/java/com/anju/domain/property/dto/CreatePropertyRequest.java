package com.anju.domain.property.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CreatePropertyRequest {

    @NotBlank
    @Size(max = 64)
    private String code;

    @Size(max = 32)
    private String status;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal rent;

    @NotNull
    @DecimalMin(value = "0.00")
    private BigDecimal deposit;

    @NotNull
    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @Size(max = 32)
    private String complianceStatus;

    private List<@Size(max = 255) String> mediaRefs;

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
}
