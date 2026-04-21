package com.anju.domain.finance;

import com.anju.aspect.Auditable;
import com.anju.common.Result;
import com.anju.domain.auth.AuthService;
import com.anju.domain.finance.dto.ExceptionMarkRequest;
import com.anju.domain.finance.dto.InvoiceIssueRequest;
import com.anju.domain.finance.dto.BookkeepingImportRequest;
import com.anju.domain.finance.dto.BookkeepingImportResponse;
import com.anju.domain.finance.dto.RefundRequest;
import com.anju.domain.finance.dto.SettlementRequest;
import com.anju.domain.finance.dto.TransactionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/finance")
@Tag(name = "Finance", description = "Financial transaction, refund, settlement, and invoice endpoints")
public class TransactionController {

    private final TransactionService transactionService;
    private final AuthService authService;

    public TransactionController(TransactionService transactionService, AuthService authService) {
        this.transactionService = transactionService;
        this.authService = authService;
    }

    @PostMapping("/bookkeeping")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Operation(summary = "Bookkeeping transaction")
    public Result<Transaction> record(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return Result.success(transactionService.recordTransaction(request, idempotencyKey));
    }

    @PostMapping("/import/bookkeeping")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Operation(summary = "Import bookkeeping rows", description = "Imports bookkeeping rows with field mapping, type validation, and idempotency-safe writes.")
    public Result<BookkeepingImportResponse> importBookkeeping(@Valid @RequestBody BookkeepingImportRequest request) {
        return Result.success(transactionService.importBookkeeping(request));
    }
    
    @Auditable(module = "FINANCE", action = "Create transaction")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Operation(summary = "Create transaction")
    public Result<Transaction> create(
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader("X-Secondary-Password") String secondaryPassword,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        authService.verifySecondaryPassword(authentication.getName(), secondaryPassword);
        return Result.success(transactionService.recordTransaction(request, idempotencyKey));
    }

    @GetMapping("/{transactionNo}")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','AUDITOR')")
    @Operation(summary = "Get transaction by number")
    public Result<Transaction> getByTransactionNo(@PathVariable String transactionNo) {
        return Result.success(transactionService.getTransactionByNo(transactionNo));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','AUDITOR')")
    @Operation(summary = "List transactions (paginated)")
    public Result<List<Transaction>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(transactionService.listTransactions(page, size));
    }

    @Auditable(module = "FINANCE", action = "Create refund")
    @PostMapping("/{transactionNo}/refund")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Operation(summary = "Create refund")
    public Result<Transaction> refund(@PathVariable String transactionNo, @Valid @RequestBody RefundRequest request) {
        return Result.success(transactionService.createRefund(transactionNo, request));
    }

    @Auditable(module = "FINANCE", action = "Daily settlement")
    @PostMapping("/settlements/daily")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Operation(summary = "Run daily settlement")
    public Result<Map<String, Object>> settleDaily(@Valid @RequestBody SettlementRequest request) {
        return Result.success(transactionService.settleDaily(request));
    }

    @GetMapping("/statements/daily")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','AUDITOR')")
    @Operation(summary = "Get daily statement")
    public Result<Map<String, Object>> dailyStatement(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(transactionService.dailyStatement(date));
    }

    @GetMapping(value = "/statements/daily/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE','AUDITOR')")
    @Operation(summary = "Export daily statement as CSV")
    public ResponseEntity<String> dailyStatementExport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> statement = transactionService.dailyStatement(date);
        StringBuilder csv = new StringBuilder();
        csv.append("date,paymentCount,paymentTotal,refundCount,refundTotal,")
           .append("exceptionCount,exceptionAmount,netAmount\n");
        csv.append(statement.get("date")).append(",")
           .append(statement.get("paymentCount")).append(",")
           .append(statement.get("paymentTotal")).append(",")
           .append(statement.get("refundCount")).append(",")
           .append(statement.get("refundTotal")).append(",")
           .append(statement.get("exceptionCount")).append(",")
           .append(statement.get("exceptionAmount")).append(",")
           .append(statement.get("netAmount")).append("\n");
        String filename = "statement-" + date + ".csv";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    @Auditable(module = "FINANCE", action = "Mark transaction exception")
    @PostMapping("/{transactionNo}/exception")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Operation(summary = "Mark transaction as exception")
    public Result<Transaction> markException(
            @PathVariable String transactionNo,
            @Valid @RequestBody ExceptionMarkRequest request) {
        return Result.success(transactionService.markAsException(transactionNo, request.getReason()));
    }

    @Auditable(module = "FINANCE", action = "Request invoice")
    @PostMapping("/{transactionNo}/invoice/request")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Operation(summary = "Request invoice")
    public Result<Transaction> requestInvoice(@PathVariable String transactionNo) {
        return Result.success(transactionService.requestInvoice(transactionNo));
    }

    @Auditable(module = "FINANCE", action = "Issue invoice")
    @PostMapping("/{transactionNo}/invoice/issue")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @Operation(summary = "Issue invoice")
    public Result<Transaction> issueInvoice(
            @PathVariable String transactionNo,
            @Valid @RequestBody InvoiceIssueRequest request,
            @RequestHeader("X-Secondary-Password") String secondaryPassword,
            Authentication authentication) {
        authService.verifySecondaryPassword(authentication.getName(), secondaryPassword);
        return Result.success(transactionService.issueInvoice(transactionNo, request));
    }
}
