package com.anju.domain.finance;

import com.anju.common.BusinessException;
import com.anju.domain.auth.CurrentUserService;
import com.anju.domain.auth.User;
import com.anju.domain.finance.dto.InvoiceIssueRequest;
import com.anju.domain.finance.dto.BookkeepingImportRequest;
import com.anju.domain.finance.dto.BookkeepingImportResponse;
import com.anju.domain.finance.dto.BookkeepingImportError;
import com.anju.domain.finance.dto.RefundRequest;
import com.anju.domain.finance.dto.SettlementRequest;
import com.anju.domain.finance.dto.TransactionRequest;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
public class TransactionService {

    private static final String DEFAULT_IMPORT_IDEMPOTENCY_PREFIX = "import-bookkeeping";
    private static final java.util.Set<String> ALLOWED_IMPORT_TYPES = java.util.Set.of("PAYMENT", "REFUND");

    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;

    public TransactionService(TransactionRepository transactionRepository,
                              CurrentUserService currentUserService) {
        this.transactionRepository = transactionRepository;
        this.currentUserService = currentUserService;
    }

    public Transaction recordTransaction(TransactionRequest request) {
        return recordTransaction(request, null);
    }

    public Transaction recordTransaction(TransactionRequest request, String idempotencyKey) {
        if (StringUtils.hasText(idempotencyKey)) {
            Transaction existing = transactionRepository.findByIdempotencyKey(idempotencyKey.trim())
                    .orElse(null);
            if (existing != null) {
                enforceTransactionAccess(existing);
                return existing;
            }
        }
        if (transactionRepository.existsByTransactionNo(request.getTransactionNumber())) {
            throw new BusinessException(400, "Transaction already exists");
        }

        User currentUser = currentUserService.requireCurrentUser();
        Long payerId = resolvePayerId(currentUser, request.getPayerId());

        Transaction transaction = new Transaction();
        transaction.setTransactionNo(request.getTransactionNumber());
        transaction.setAppointmentId(request.getAppointmentId());
        transaction.setPayerId(payerId);
        transaction.setAmount(request.getAmount());
        transaction.setType(request.getType());
        transaction.setCurrency(StringUtils.hasText(request.getCurrency()) ? request.getCurrency().trim().toUpperCase() : "USD");
        transaction.setStatus("SUCCESS");
        transaction.setChannel(StringUtils.hasText(request.getChannel()) ? request.getChannel().trim().toUpperCase() : "MANUAL");
        transaction.setRefundable(request.getRefundable() == null ? true : request.getRefundable());
        transaction.setExceptionFlag(false);
        transaction.setExceptionReason(null);
        transaction.setIdempotencyKey(StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : null);
        transaction.setRemark(request.getRemark());
        transaction.setOccurredAt(LocalDateTime.now());
        return transactionRepository.save(transaction);
    }

    public BookkeepingImportResponse importBookkeeping(BookkeepingImportRequest request) {
        BookkeepingImportResponse response = new BookkeepingImportResponse();
        response.setTotalRows(request.getRows().size());

        List<String> importedNumbers = new ArrayList<>();
        List<BookkeepingImportError> errors = new ArrayList<>();

        String keyPrefix = StringUtils.hasText(request.getIdempotencyKeyPrefix())
                ? request.getIdempotencyKeyPrefix().trim()
                : DEFAULT_IMPORT_IDEMPOTENCY_PREFIX;

        int rowNumber = 0;
        for (Map<String, String> row : request.getRows()) {
            rowNumber++;
            try {
                TransactionRequest transactionRequest = toTransactionRequest(request.getFieldMapping(), row);
                String idempotencyKey = keyPrefix + ":" + transactionRequest.getTransactionNumber();
                Transaction stored = recordTransaction(transactionRequest, idempotencyKey);

                String occurredAtText = getMappedValue(request.getFieldMapping(), row, "occurredAt");
                if (StringUtils.hasText(occurredAtText)) {
                    try {
                        stored.setOccurredAt(LocalDateTime.parse(occurredAtText.trim()));
                        transactionRepository.save(stored);
                    } catch (DateTimeParseException ex) {
                        throw new IllegalArgumentException("occurredAt must be ISO-8601 datetime (example: 2026-03-25T10:15:30)");
                    }
                }

                importedNumbers.add(stored.getTransactionNo());
            } catch (BusinessException ex) {
                errors.add(new BookkeepingImportError(rowNumber, "code=" + ex.getCode() + ", message=" + ex.getMessage()));
            } catch (IllegalArgumentException ex) {
                errors.add(new BookkeepingImportError(rowNumber, ex.getMessage()));
            }
        }

        response.setImportedTransactionNumbers(importedNumbers);
        response.setErrors(errors);
        response.setSuccessCount(importedNumbers.size());
        response.setFailureCount(errors.size());
        return response;
    }

    public Transaction getTransactionByNo(String transactionNo) {
        Transaction transaction = transactionRepository.findByTransactionNo(transactionNo)
                .orElseThrow(() -> new BusinessException(4040, "Transaction not found"));
        enforceTransactionAccess(transaction);
        return transaction;
    }

    public List<Transaction> listTransactions(int page, int size) {
        int safeSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "occurredAt"));
        User currentUser = currentUserService.requireCurrentUser();
        if ("STAFF".equalsIgnoreCase(currentUser.getRole())) {
            return transactionRepository.findByPayerId(currentUser.getId(), pageable).getContent();
        }
        return transactionRepository.findAll(pageable).getContent();
    }

    public Transaction createRefund(String transactionNo, RefundRequest request) {
        Transaction original = getTransactionByNo(transactionNo);
        if (!"PAYMENT".equalsIgnoreCase(original.getType())) {
            throw new BusinessException(4004, "Only PAYMENT transactions can be refunded.");
        }
        if (!"SUCCESS".equalsIgnoreCase(original.getStatus()) && !"SETTLED".equalsIgnoreCase(original.getStatus())) {
            throw new BusinessException(4005, "Only successful or settled transactions can be refunded.");
        }
        if (!Boolean.TRUE.equals(original.getRefundable())) {
            throw new BusinessException(4008, "This transaction is marked as non-refundable.");
        }
        if (request.getAmount().compareTo(original.getAmount()) > 0) {
            throw new BusinessException(4006, "Refund amount cannot exceed original transaction amount.");
        }

        Transaction refund = new Transaction();
        refund.setTransactionNo(generateTransactionNo("RF"));
        refund.setAppointmentId(original.getAppointmentId());
        refund.setPayerId(original.getPayerId());
        refund.setType("REFUND");
        refund.setAmount(request.getAmount());
        refund.setCurrency(original.getCurrency());
        refund.setStatus("SUCCESS");
        refund.setChannel(original.getChannel());
        refund.setRefundable(false);
        refund.setExceptionFlag(false);
        refund.setExceptionReason(null);
        refund.setOccurredAt(LocalDateTime.now());
        refund.setRelatedTransactionNo(original.getTransactionNo());
        refund.setRemark(request.getReason());
        return transactionRepository.save(refund);
    }

    public Map<String, Object> settleDaily(SettlementRequest request) {
        LocalDateTime start = request.getDate().atStartOfDay();
        LocalDateTime end = request.getDate().plusDays(1).atStartOfDay();
        List<Transaction> transactions = transactionRepository.findByStatusAndOccurredAtBetween("SUCCESS", start, end);

        BigDecimal totalAmount = BigDecimal.ZERO;
        int settledCount = 0;
        for (Transaction tx : transactions) {
            if (!"PAYMENT".equalsIgnoreCase(tx.getType())) {
                continue;
            }
            tx.setStatus("SETTLED");
            tx.setSettledAt(LocalDateTime.now());
            transactionRepository.save(tx);
            settledCount++;
            totalAmount = totalAmount.add(tx.getAmount());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("date", request.getDate());
        result.put("settledCount", settledCount);
        result.put("settledAmount", totalAmount);
        return result;
    }

    public Map<String, Object> dailyStatement(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay();
        List<Transaction> transactions = transactionRepository.findByOccurredAtBetween(start, end);

        BigDecimal paymentTotal = BigDecimal.ZERO;
        BigDecimal refundTotal = BigDecimal.ZERO;
        BigDecimal exceptionAmount = BigDecimal.ZERO;
        int paymentCount = 0;
        int refundCount = 0;
        int exceptionCount = 0;
        for (Transaction tx : transactions) {
            if ("PAYMENT".equalsIgnoreCase(tx.getType())) {
                paymentCount++;
                paymentTotal = paymentTotal.add(tx.getAmount());
            }
            if ("REFUND".equalsIgnoreCase(tx.getType())) {
                refundCount++;
                refundTotal = refundTotal.add(tx.getAmount());
            }
            if (Boolean.TRUE.equals(tx.getExceptionFlag())) {
                exceptionCount++;
                exceptionAmount = exceptionAmount.add(tx.getAmount());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("date", date);
        result.put("paymentCount", paymentCount);
        result.put("paymentTotal", paymentTotal);
        result.put("refundCount", refundCount);
        result.put("refundTotal", refundTotal);
        result.put("exceptionCount", exceptionCount);
        result.put("exceptionAmount", exceptionAmount);
        result.put("netAmount", paymentTotal.subtract(refundTotal));
        return result;
    }

    public Transaction markAsException(String transactionNo, String reason) {
        Transaction transaction = getTransactionByNo(transactionNo);
        if (Boolean.TRUE.equals(transaction.getExceptionFlag())) {
            throw new BusinessException(4092, "Transaction is already marked as exception.");
        }
        transaction.setExceptionFlag(true);
        transaction.setExceptionReason(reason.trim());
        return transactionRepository.save(transaction);
    }

    public Transaction requestInvoice(String transactionNo) {
        Transaction transaction = getTransactionByNo(transactionNo);
        if ("REQUESTED".equalsIgnoreCase(transaction.getInvoiceStatus()) || "ISSUED".equalsIgnoreCase(transaction.getInvoiceStatus())) {
            throw new BusinessException(4091, "Invoice is already requested or issued.");
        }
        transaction.setInvoiceStatus("REQUESTED");
        return transactionRepository.save(transaction);
    }

    public Transaction issueInvoice(String transactionNo, InvoiceIssueRequest request) {
        Transaction transaction = getTransactionByNo(transactionNo);
        if (!"REQUESTED".equalsIgnoreCase(transaction.getInvoiceStatus())) {
            throw new BusinessException(4007, "Invoice must be requested before issue.");
        }
        transaction.setInvoiceNo(request.getInvoiceNo().trim());
        transaction.setInvoiceStatus("ISSUED");
        return transactionRepository.save(transaction);
    }

    private Long resolvePayerId(User currentUser, Long payerIdFromRequest) {
        if ("ADMIN".equalsIgnoreCase(currentUser.getRole()) || "FINANCE".equalsIgnoreCase(currentUser.getRole())) {
            return payerIdFromRequest != null ? payerIdFromRequest : currentUser.getId();
        }
        if (payerIdFromRequest != null && !payerIdFromRequest.equals(currentUser.getId())) {
            throw new BusinessException(4032, "You cannot create transactions for another user.");
        }
        return currentUser.getId();
    }

    private void enforceTransactionAccess(Transaction transaction) {
        User currentUser = currentUserService.requireCurrentUser();
        if ("STAFF".equalsIgnoreCase(currentUser.getRole())
                && transaction.getPayerId() != null
                && !currentUser.getId().equals(transaction.getPayerId())) {
            throw new BusinessException(4033, "You are not allowed to access this transaction.");
        }
    }

    private String generateTransactionNo(String prefix) {
        for (int i = 0; i < 10; i++) {
            String candidate = prefix + "-" + System.currentTimeMillis() + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
            if (!transactionRepository.existsByTransactionNo(candidate)) {
                return candidate;
            }
        }
        throw new BusinessException(5001, "Unable to generate transaction number.");
    }

    private TransactionRequest toTransactionRequest(Map<String, String> fieldMapping, Map<String, String> row) {
        TransactionRequest request = new TransactionRequest();

        String transactionNumber = requireMappedValue(fieldMapping, row, "transactionNumber");
        String amountText = requireMappedValue(fieldMapping, row, "amount");
        String type = requireMappedValue(fieldMapping, row, "type").toUpperCase();

        if (!ALLOWED_IMPORT_TYPES.contains(type)) {
            throw new IllegalArgumentException("type must be PAYMENT or REFUND");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountText.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("amount must be a valid decimal number");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than 0");
        }

        request.setTransactionNumber(transactionNumber.trim());
        request.setAmount(amount);
        request.setType(type);

        String appointmentIdText = getMappedValue(fieldMapping, row, "appointmentId");
        if (StringUtils.hasText(appointmentIdText)) {
            request.setAppointmentId(parsePositiveLong("appointmentId", appointmentIdText));
        }

        String payerIdText = getMappedValue(fieldMapping, row, "payerId");
        if (StringUtils.hasText(payerIdText)) {
            request.setPayerId(parsePositiveLong("payerId", payerIdText));
        }

        String currency = getMappedValue(fieldMapping, row, "currency");
        if (StringUtils.hasText(currency)) {
            request.setCurrency(currency.trim());
        }

        String channel = getMappedValue(fieldMapping, row, "channel");
        if (StringUtils.hasText(channel)) {
            request.setChannel(channel.trim());
        }

        String remark = getMappedValue(fieldMapping, row, "remark");
        if (StringUtils.hasText(remark)) {
            request.setRemark(remark.trim());
        }

        String refundable = getMappedValue(fieldMapping, row, "refundable");
        if (StringUtils.hasText(refundable)) {
            if (!"true".equalsIgnoreCase(refundable.trim()) && !"false".equalsIgnoreCase(refundable.trim())) {
                throw new IllegalArgumentException("refundable must be true or false");
            }
            request.setRefundable(Boolean.parseBoolean(refundable.trim()));
        }

        return request;
    }

    private String requireMappedValue(Map<String, String> fieldMapping, Map<String, String> row, String canonicalField) {
        String value = getMappedValue(fieldMapping, row, canonicalField);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(canonicalField + " is required");
        }
        return value;
    }

    private String getMappedValue(Map<String, String> fieldMapping, Map<String, String> row, String canonicalField) {
        String sourceField = canonicalField;
        if (fieldMapping != null && StringUtils.hasText(fieldMapping.get(canonicalField))) {
            sourceField = fieldMapping.get(canonicalField).trim();
        }
        return row.get(sourceField);
    }

    private Long parsePositiveLong(String name, String value) {
        try {
            long parsed = Long.parseLong(value.trim());
            if (parsed <= 0) {
                throw new IllegalArgumentException(name + " must be a positive integer");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be a positive integer");
        }
    }
}
