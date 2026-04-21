package com.anju.domain.finance.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

public class BookkeepingImportRequest {

    private String idempotencyKeyPrefix;
    private Map<String, String> fieldMapping;

    @NotEmpty
    private List<Map<String, String>> rows;

    public String getIdempotencyKeyPrefix() {
        return idempotencyKeyPrefix;
    }

    public void setIdempotencyKeyPrefix(String idempotencyKeyPrefix) {
        this.idempotencyKeyPrefix = idempotencyKeyPrefix;
    }

    public Map<String, String> getFieldMapping() {
        return fieldMapping;
    }

    public void setFieldMapping(Map<String, String> fieldMapping) {
        this.fieldMapping = fieldMapping;
    }

    public List<Map<String, String>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, String>> rows) {
        this.rows = rows;
    }
}
