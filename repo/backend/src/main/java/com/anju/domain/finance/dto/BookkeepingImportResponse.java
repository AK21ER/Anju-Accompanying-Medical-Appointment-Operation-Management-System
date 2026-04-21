package com.anju.domain.finance.dto;

import java.util.ArrayList;
import java.util.List;

public class BookkeepingImportResponse {

    private int totalRows;
    private int successCount;
    private int failureCount;
    private List<String> importedTransactionNumbers = new ArrayList<>();
    private List<BookkeepingImportError> errors = new ArrayList<>();

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public List<String> getImportedTransactionNumbers() {
        return importedTransactionNumbers;
    }

    public void setImportedTransactionNumbers(List<String> importedTransactionNumbers) {
        this.importedTransactionNumbers = importedTransactionNumbers;
    }

    public List<BookkeepingImportError> getErrors() {
        return errors;
    }

    public void setErrors(List<BookkeepingImportError> errors) {
        this.errors = errors;
    }
}
