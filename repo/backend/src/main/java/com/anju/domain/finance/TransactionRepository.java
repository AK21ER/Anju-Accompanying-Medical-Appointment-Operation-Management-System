package com.anju.domain.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    boolean existsByTransactionNo(String transactionNo);

    Optional<Transaction> findByTransactionNo(String transactionNo);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    List<Transaction> findByOccurredAtBetween(LocalDateTime start, LocalDateTime end);

    List<Transaction> findByStatusAndOccurredAtBetween(String status, LocalDateTime start, LocalDateTime end);

    List<Transaction> findByPayerIdOrderByOccurredAtDesc(Long payerId);

    Page<Transaction> findByPayerId(Long payerId, Pageable pageable);

    Optional<Transaction> findTopByAppointmentIdOrderByOccurredAtDesc(Long appointmentId);
}
