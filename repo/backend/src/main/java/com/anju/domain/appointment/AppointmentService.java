package com.anju.domain.appointment;

import com.anju.common.BusinessException;
import com.anju.domain.auth.CurrentUserService;
import com.anju.domain.auth.User;
import com.anju.domain.appointment.dto.CreateAppointmentRequest;
import com.anju.domain.appointment.dto.RescheduleAppointmentRequest;
import com.anju.domain.appointment.dto.UpdateAppointmentRequest;
import com.anju.domain.finance.TransactionRepository;
import com.anju.domain.property.PropertyRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
public class AppointmentService {

    private static final DateTimeFormatter NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Set<String> RESCHEDULABLE_STATUSES = Set.of("PENDING", "CONFIRMED", "RESCHEDULED");
    private static final Set<String> CANCELLABLE_STATUSES = Set.of("PENDING", "CONFIRMED", "RESCHEDULED");
    private static final BigDecimal DEFAULT_ORDER_AMOUNT = new BigDecimal("500.00");

    private final AppointmentRepository appointmentRepository;
    private final PropertyRepository propertyRepository;
    private final CurrentUserService currentUserService;
    private final TransactionRepository transactionRepository;

    public AppointmentService(AppointmentRepository appointmentRepository,
                              PropertyRepository propertyRepository,
                              CurrentUserService currentUserService,
                              TransactionRepository transactionRepository) {
        this.appointmentRepository = appointmentRepository;
        this.propertyRepository = propertyRepository;
        this.currentUserService = currentUserService;
        this.transactionRepository = transactionRepository;
    }

    public Appointment create(CreateAppointmentRequest request) {
        return create(request, null);
    }

    public Appointment create(CreateAppointmentRequest request, String idempotencyKey) {
        User user = currentUserService.requireCurrentUser();
        if (StringUtils.hasText(idempotencyKey)) {
            Appointment existing = appointmentRepository.findByIdempotencyKey(idempotencyKey.trim())
                    .orElse(null);
            if (existing != null) {
                enforceAppointmentAccess(existing);
                return existing;
            }
        }
        if ("STAFF".equalsIgnoreCase(user.getRole()) && !user.getId().equals(request.getStaffId())) {
            throw new BusinessException(4031, "You cannot create appointments for another staff member.");
        }
        validateTimeRange(request.getStartTime(), request.getEndTime());
        if (!propertyRepository.existsById(request.getResourceId())) {
            throw new BusinessException(4041, "Resource property does not exist.");
        }
        boolean conflict = appointmentRepository.existsConflict(
                request.getStaffId(),
                request.getResourceId(),
                request.getStartTime(),
                request.getEndTime(),
                "CANCELLED"
        );
        if (conflict) {
            throw new BusinessException(4006, "Appointment time conflicts with existing schedule.");
        }

        Appointment appointment = new Appointment();
        appointment.setNumber(resolveNumber(request.getNumber()));
        appointment.setStaffId(request.getStaffId());
        appointment.setResourceId(request.getResourceId());
        appointment.setStartTime(request.getStartTime());
        appointment.setEndTime(request.getEndTime());
        // Lifecycle fields are server-controlled to keep state machine behavior consistent.
        appointment.setStatus("PENDING");
        appointment.setPenalty(BigDecimal.ZERO);
        appointment.setRescheduleCount(0);
        appointment.setIdempotencyKey(StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : null);
        return appointmentRepository.save(appointment);
    }

    public Appointment reschedule(Long id, RescheduleAppointmentRequest request) {
        Appointment appointment = getById(id);
        enforceAppointmentAccess(appointment);
        ensureReschedulable(appointment);
        
        // 1. Must be at least 24 hours in advance
        LocalDateTime now = LocalDateTime.now();
        if (ChronoUnit.HOURS.between(now, appointment.getStartTime()) < 24) {
            throw new BusinessException(4010, "Cannot reschedule less than 24 hours before appointment.");
        }

        // 2. Max 2 reschedules
        if (appointment.getRescheduleCount() >= 2) {
            throw new BusinessException(4011, "Exceeded maximum of 2 reschedules.");
        }

        // 3. Time validation
        validateTimeRange(request.getStartTime(), request.getEndTime());

        // 4. Overlapping conflict
        boolean conflict = appointmentRepository.existsConflictExcludingId(
            appointment.getId(),
            appointment.getStaffId(),
            appointment.getResourceId(),
                request.getStartTime(),
                request.getEndTime(),
                "CANCELLED"
        );
        if (conflict) {
            throw new BusinessException(4006, "Appointment time conflicts with existing schedule.");
        }

        appointment.setStartTime(request.getStartTime());
        appointment.setEndTime(request.getEndTime());
        appointment.setRescheduleCount(appointment.getRescheduleCount() + 1);
        appointment.setStatus("RESCHEDULED");

        return appointmentRepository.save(appointment);
    }

    public Appointment update(Long id, UpdateAppointmentRequest request) {
        Appointment appointment = getById(id);
        enforceAppointmentAccess(appointment);

        validateTimeRange(request.getStartTime(), request.getEndTime());
        if (!propertyRepository.existsById(request.getResourceId())) {
            throw new BusinessException(4041, "Resource property does not exist.");
        }

        boolean conflict = appointmentRepository.existsConflictExcludingId(
                appointment.getId(),
                request.getStaffId(),
                request.getResourceId(),
                request.getStartTime(),
                request.getEndTime(),
                "CANCELLED"
        );
        if (conflict) {
            throw new BusinessException(4006, "Appointment time conflicts with existing schedule.");
        }

        appointment.setStaffId(request.getStaffId());
        appointment.setResourceId(request.getResourceId());
        appointment.setStartTime(request.getStartTime());
        appointment.setEndTime(request.getEndTime());
        return appointmentRepository.save(appointment);
    }

    public void delete(Long id) {
        Appointment appointment = getById(id);
        enforceAppointmentAccess(appointment);
        appointmentRepository.delete(appointment);
    }

    public void cancel(Long id) {
        Appointment appointment = getById(id);
        enforceAppointmentAccess(appointment);
        ensureCancellable(appointment);
        LocalDateTime now = LocalDateTime.now();
        
        if (ChronoUnit.HOURS.between(now, appointment.getStartTime()) < 24) {
            BigDecimal orderAmount = transactionRepository
                    .findTopByAppointmentIdOrderByOccurredAtDesc(appointment.getId())
                    .map(tx -> tx.getAmount())
                    .orElse(DEFAULT_ORDER_AMOUNT);
            BigDecimal penaltyPercentage = orderAmount.multiply(new BigDecimal("0.10"));
            BigDecimal maxPenalty = new BigDecimal("50.00");
            appointment.setPenalty(penaltyPercentage.min(maxPenalty));
        }
        
        appointment.setStatus("CANCELLED");
        appointmentRepository.save(appointment);
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedRate = 60000)
    public void autoReleaseUnconfirmedAppointments() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);
        List<Appointment> pending = appointmentRepository.findByStatusAndCreatedAtBefore("PENDING", threshold);
        for (Appointment appt : pending) {
            appt.setStatus("RELEASED");
            appointmentRepository.save(appt);
        }
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public Appointment getById(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(4042, "Appointment not found."));
        enforceAppointmentAccess(appointment);
        return appointment;
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<Appointment> list(int page, int size) {
        int safeSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "startTime"));
        User user = currentUserService.requireCurrentUser();
        if ("STAFF".equalsIgnoreCase(user.getRole())) {
            return appointmentRepository.findByStaffId(user.getId(), pageable).getContent();
        }
        return appointmentRepository.findAll(pageable).getContent();
    }

    private void enforceAppointmentAccess(Appointment appointment) {
        User user = currentUserService.requireCurrentUser();
        if ("STAFF".equalsIgnoreCase(user.getRole()) && !user.getId().equals(appointment.getStaffId())) {
            throw new BusinessException(4030, "You are not allowed to access this appointment.");
        }
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new BusinessException(4007, "endTime must be after startTime.");
        }
        long diffMinutes = ChronoUnit.MINUTES.between(startTime, endTime);
        if (diffMinutes != 15 && diffMinutes != 30 && diffMinutes != 60 && diffMinutes != 90) {
            throw new BusinessException(4012, "Service duration must be 15, 30, 60, or 90 minutes.");
        }
    }

    private String resolveNumber(String inputNumber) {
        if (StringUtils.hasText(inputNumber)) {
            String number = inputNumber.trim().toUpperCase();
            if (appointmentRepository.existsByNumber(number)) {
                throw new BusinessException(4008, "Appointment number already exists.");
            }
            return number;
        }

        for (int attempt = 0; attempt < 8; attempt++) {
            String generated = "APPT-" + LocalDateTime.now().format(NUMBER_FORMATTER)
                    + "-" + ThreadLocalRandom.current().nextInt(1000, 10000);
            if (!appointmentRepository.existsByNumber(generated)) {
                return generated;
            }
        }
        throw new BusinessException(4009, "Unable to generate unique appointment number.");
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : defaultValue;
    }

    private void ensureReschedulable(Appointment appointment) {
        String status = normalizeOrDefault(appointment.getStatus(), "PENDING");
        if (!RESCHEDULABLE_STATUSES.contains(status)) {
            throw new BusinessException(4013, "Only PENDING/CONFIRMED/RESCHEDULED appointments can be rescheduled.");
        }
    }

    private void ensureCancellable(Appointment appointment) {
        String status = normalizeOrDefault(appointment.getStatus(), "PENDING");
        if (!CANCELLABLE_STATUSES.contains(status)) {
            throw new BusinessException(4014, "Only PENDING/CONFIRMED/RESCHEDULED appointments can be cancelled.");
        }
    }
}
