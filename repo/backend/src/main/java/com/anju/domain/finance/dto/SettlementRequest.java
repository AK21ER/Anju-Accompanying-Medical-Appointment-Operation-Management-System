package com.anju.domain.finance.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class SettlementRequest {

    @NotNull
    private LocalDate date;

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }
}
