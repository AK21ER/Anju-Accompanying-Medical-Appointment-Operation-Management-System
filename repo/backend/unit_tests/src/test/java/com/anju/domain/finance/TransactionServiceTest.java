package com.anju.domain.finance;

import com.anju.common.BusinessException;
import com.anju.domain.auth.CurrentUserService;
import com.anju.domain.auth.User;
import com.anju.domain.finance.dto.BookkeepingImportRequest;
import com.anju.domain.finance.dto.BookkeepingImportResponse;
import com.anju.domain.finance.dto.TransactionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private TransactionService transactionService;

    private User currentUser;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setRole("FINANCE");
        lenient().when(currentUserService.requireCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void recordTransactionRejectsDuplicateTransactionNo() {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionNumber("TX-100");
        request.setAmount(new BigDecimal("100.00"));
        request.setType("PAYMENT");
        request.setPayerId(1L);

        when(transactionRepository.existsByTransactionNo("TX-100")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> transactionService.recordTransaction(request));
        assertEquals(400, ex.getCode());
    }

    @Test
    void getTransactionByNoReturnsEntity() {
        Transaction tx = new Transaction();
        tx.setTransactionNo("TX-200");

        when(transactionRepository.findByTransactionNo("TX-200")).thenReturn(Optional.of(tx));

        Transaction result = transactionService.getTransactionByNo("TX-200");
        assertEquals("TX-200", result.getTransactionNo());
    }

    @Test
    void recordTransactionSetsDefaults() {
        TransactionRequest request = new TransactionRequest();
        request.setTransactionNumber("TX-300");
        request.setAmount(new BigDecimal("250.00"));
        request.setType("PAYMENT");
        request.setPayerId(1L);

        when(transactionRepository.existsByTransactionNo("TX-300")).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Transaction result = transactionService.recordTransaction(request);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals("USD", result.getCurrency());
    }

    @Test
    void recordTransactionWithIdempotencyKeyReturnsExistingTransaction() {
        Transaction existing = new Transaction();
        existing.setTransactionNo("TX-301");
        existing.setPayerId(1L);

        when(transactionRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

        TransactionRequest request = new TransactionRequest();
        request.setTransactionNumber("TX-NEW");
        request.setAmount(new BigDecimal("10.00"));
        request.setType("PAYMENT");

        Transaction result = transactionService.recordTransaction(request, "idem-1");
        assertEquals("TX-301", result.getTransactionNo());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void createRefundRejectsNonRefundableTransaction() {
        Transaction original = new Transaction();
        original.setTransactionNo("TX-REF-1");
        original.setType("PAYMENT");
        original.setStatus("SUCCESS");
        original.setRefundable(false);
        original.setAmount(new BigDecimal("100.00"));
        original.setPayerId(1L);

        when(transactionRepository.findByTransactionNo("TX-REF-1")).thenReturn(Optional.of(original));

        com.anju.domain.finance.dto.RefundRequest request = new com.anju.domain.finance.dto.RefundRequest();
        request.setAmount(new BigDecimal("10.00"));
        request.setReason("manual check");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> transactionService.createRefund("TX-REF-1", request));
        assertEquals(4008, ex.getCode());
    }

    @Test
    void importBookkeepingProcessesValidRowsAndCollectsErrors() {
        BookkeepingImportRequest request = new BookkeepingImportRequest();
        request.setFieldMapping(Map.of(
                "transactionNumber", "tx_no",
                "amount", "amt",
                "type", "kind"
        ));
        request.setIdempotencyKeyPrefix("batch-1");
        request.setRows(List.of(
                Map.of("tx_no", "TX-900", "amt", "10.50", "kind", "PAYMENT", "currency", "USD"),
                Map.of("tx_no", "TX-901", "amt", "not-number", "kind", "PAYMENT")
        ));

        when(transactionRepository.findByIdempotencyKey("batch-1:TX-900")).thenReturn(Optional.empty());
        when(transactionRepository.existsByTransactionNo("TX-900")).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookkeepingImportResponse result = transactionService.importBookkeeping(request);

        assertEquals(2, result.getTotalRows());
        assertEquals(1, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals("TX-900", result.getImportedTransactionNumbers().get(0));
        assertEquals(2, result.getErrors().get(0).getRowNumber());
    }

    @Test
    void importBookkeepingInvalidRefundableValueCollectsError() {
        BookkeepingImportRequest request = new BookkeepingImportRequest();
        request.setRows(List.of(Map.of(
                "transactionNumber", "TX-REFBOOL-1",
                "amount", "10.00",
                "type", "PAYMENT",
                "refundable", "yes"
        )));

        BookkeepingImportResponse result = transactionService.importBookkeeping(request);

        assertEquals(1, result.getTotalRows());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(1, result.getErrors().get(0).getRowNumber());
    }

    @Test
    void importBookkeepingInvalidPayerIdFormatCollectsError() {
        BookkeepingImportRequest request = new BookkeepingImportRequest();
        request.setRows(List.of(Map.of(
                "transactionNumber", "TX-PAYERID-1",
                "amount", "20.00",
                "type", "PAYMENT",
                "payerId", "not-a-number"
        )));

        BookkeepingImportResponse result = transactionService.importBookkeeping(request);

        assertEquals(1, result.getTotalRows());
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailureCount());
        assertEquals(1, result.getErrors().get(0).getRowNumber());
    }

    @Test
    void importBookkeepingParsesOccurredAtIsoDate() {
        BookkeepingImportRequest request = new BookkeepingImportRequest();
        request.setRows(List.of(Map.of(
                "transactionNumber", "TX-902",
                "amount", "20.00",
                "type", "PAYMENT",
                "occurredAt", "2026-03-25T09:30:00"
        )));

        when(transactionRepository.findByIdempotencyKey("import-bookkeeping:TX-902")).thenReturn(Optional.empty());
        when(transactionRepository.existsByTransactionNo("TX-902")).thenReturn(false);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookkeepingImportResponse result = transactionService.importBookkeeping(request);

        assertEquals(1, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());
        verify(transactionRepository, atLeastOnce()).save(any(Transaction.class));
    }
}
