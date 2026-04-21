package com.anju.domain.finance.dto;

import jakarta.validation.constraints.NotBlank;

public class InvoiceIssueRequest {

    @NotBlank
    private String invoiceNo;

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public void setInvoiceNo(String invoiceNo) {
        this.invoiceNo = invoiceNo;
    }
}
