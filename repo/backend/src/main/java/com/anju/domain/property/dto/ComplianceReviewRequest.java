package com.anju.domain.property.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ComplianceReviewRequest {

    @NotBlank
    @Pattern(regexp = "PENDING|APPROVED|REJECTED")
    private String complianceStatus;

    @Size(max = 255)
    private String comment;

    public String getComplianceStatus() {
        return complianceStatus;
    }

    public void setComplianceStatus(String complianceStatus) {
        this.complianceStatus = complianceStatus;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
