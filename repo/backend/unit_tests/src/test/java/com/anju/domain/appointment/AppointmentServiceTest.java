package com.anju.domain.appointment;

import com.anju.common.BusinessException;
import com.anju.domain.auth.CurrentUserService;
import com.anju.domain.auth.User;
import com.anju.domain.appointment.dto.RescheduleAppointmentRequest;
import com.anju.domain.appointment.dto.CreateAppointmentRequest;
import com.anju.domain.finance.Transaction;
import com.anju.domain.finance.TransactionRepository;
import com.anju.domain.property.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private AppointmentService appointmentService;

    private Appointment existingAppointment;
    private User currentUser;

    @BeforeEach
    void setUp() {
        existingAppointment = new Appointment();
        existingAppointment.setId(1L);
        existingAppointment.setStaffId(10L);
        existingAppointment.setResourceId(20L);
        existingAppointment.setRescheduleCount(0);
        existingAppointment.setStatus("PENDING");

        currentUser = new User();
        currentUser.setId(10L);
        currentUser.setRole("STAFF");
        lenient().when(currentUserService.requireCurrentUser()).thenReturn(currentUser);
    }

    @Test
    void testReschedule_exceedingMaxReschedules_throwsException() {
        existingAppointment.setRescheduleCount(2); // Already 2 reschedules
        existingAppointment.setStartTime(LocalDateTime.now().plusDays(2)); // More than 24h
        existingAppointment.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));
        
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(existingAppointment));

        RescheduleAppointmentRequest request = new RescheduleAppointmentRequest();
        request.setStartTime(LocalDateTime.now().plusDays(3));
        request.setEndTime(LocalDateTime.now().plusDays(3).plusHours(1));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            appointmentService.reschedule(1L, request);
        });

        assertEquals(4011, exception.getCode());
        assertEquals("Exceeded maximum of 2 reschedules.", exception.getMessage());
    }

    @Test
    void testReschedule_insufficientAdvanceNotice_throwsException() {
        // Less than 24 hours from now
        existingAppointment.setStartTime(LocalDateTime.now().plusHours(10));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(existingAppointment));

        RescheduleAppointmentRequest request = new RescheduleAppointmentRequest();
        request.setStartTime(LocalDateTime.now().plusDays(3));
        request.setEndTime(LocalDateTime.now().plusDays(3).plusHours(1));

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            appointmentService.reschedule(1L, request);
        });

        assertEquals(4010, exception.getCode());
        assertTrue(exception.getMessage().contains("less than 24 hours"));
    }

    @Test
    void testReschedule_overlappingTimeSlot_throwsException() {
        existingAppointment.setStartTime(LocalDateTime.now().plusDays(2));
        existingAppointment.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(existingAppointment));

        LocalDateTime newStartTime = LocalDateTime.now().plusDays(3);
        LocalDateTime newEndTime = LocalDateTime.now().plusDays(3).plusHours(1);

        RescheduleAppointmentRequest request = new RescheduleAppointmentRequest();
        request.setStartTime(newStartTime);
        request.setEndTime(newEndTime);

        // Simulate conflict — reschedule uses existsConflictExcludingId with the appointment's own ID
        when(appointmentRepository.existsConflictExcludingId(
                eq(1L), eq(10L), eq(20L), eq(newStartTime), eq(newEndTime), eq("CANCELLED")
        )).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            appointmentService.reschedule(1L, request);
        });

        assertEquals(4006, exception.getCode());
        assertTrue(exception.getMessage().contains("conflicts with existing schedule"));
    }

    @Test
    void testReschedule_success() {
        existingAppointment.setStartTime(LocalDateTime.now().plusDays(2));
        existingAppointment.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(existingAppointment));

        LocalDateTime newStartTime = LocalDateTime.now().plusDays(3);
        LocalDateTime newEndTime = LocalDateTime.now().plusDays(3).plusHours(1);

        RescheduleAppointmentRequest request = new RescheduleAppointmentRequest();
        request.setStartTime(newStartTime);
        request.setEndTime(newEndTime);

        when(appointmentRepository.existsConflictExcludingId(
                eq(1L), eq(10L), eq(20L), eq(newStartTime), eq(newEndTime), eq("CANCELLED")
        )).thenReturn(false);

        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = appointmentService.reschedule(1L, request);

        assertEquals(1, result.getRescheduleCount());
        assertEquals("RESCHEDULED", result.getStatus());
        assertEquals(newStartTime, result.getStartTime());
        verify(appointmentRepository).save(any(Appointment.class));
    }

    @Test
    void testCreate_withIdempotencyKey_returnsExistingAppointment() {
        Appointment existing = new Appointment();
        existing.setId(99L);
        existing.setStaffId(10L);
        existing.setNumber("APPT-EXISTING");

        when(appointmentRepository.findByIdempotencyKey("idem-appointment")).thenReturn(Optional.of(existing));

        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setStaffId(10L);
        request.setResourceId(20L);
        request.setStartTime(LocalDateTime.now().plusDays(2));
        request.setEndTime(LocalDateTime.now().plusDays(2).plusMinutes(30));

        Appointment result = appointmentService.create(request, "idem-appointment");

        assertEquals(99L, result.getId());
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void testCreate_serverControlsLifecycleFields() {
        when(propertyRepository.existsById(20L)).thenReturn(true);
        when(appointmentRepository.existsConflict(anyLong(), anyLong(), any(), any(), eq("CANCELLED"))).thenReturn(false);
        when(appointmentRepository.existsByNumber("APPT-CUSTOM")).thenReturn(false);
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setNumber("appt-custom");
        request.setStaffId(10L);
        request.setResourceId(20L);
        request.setStartTime(LocalDateTime.now().plusDays(2));
        request.setEndTime(LocalDateTime.now().plusDays(2).plusMinutes(30));
        request.setStatus("COMPLETED");
        request.setPenalty(new BigDecimal("88.88"));
        request.setRescheduleCount(2);

        Appointment result = appointmentService.create(request, null);

        assertEquals("PENDING", result.getStatus());
        assertEquals(BigDecimal.ZERO, result.getPenalty());
        assertEquals(0, result.getRescheduleCount());
    }

    @Test
    void testReschedule_releasedAppointment_throwsException() {
        existingAppointment.setStatus("RELEASED");
        existingAppointment.setStartTime(LocalDateTime.now().plusDays(2));
        existingAppointment.setEndTime(LocalDateTime.now().plusDays(2).plusHours(1));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(existingAppointment));

        RescheduleAppointmentRequest request = new RescheduleAppointmentRequest();
        request.setStartTime(LocalDateTime.now().plusDays(3));
        request.setEndTime(LocalDateTime.now().plusDays(3).plusHours(1));

        BusinessException exception = assertThrows(BusinessException.class, () -> appointmentService.reschedule(1L, request));

        assertEquals(4013, exception.getCode());
    }

    @Test
    void testCancel_withinDeadline_penaltyIsMinOf10PercentOr50() {
        // Appointment starting in 2 hours — within 24h cancellation window
        existingAppointment.setStartTime(LocalDateTime.now().plusHours(2));
        existingAppointment.setEndTime(LocalDateTime.now().plusHours(3));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(existingAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        // Order amount 300 → 10% = 30, which is < 50 cap, so penalty = 30
        Transaction tx = new Transaction();
        tx.setAmount(new BigDecimal("300.00"));
        when(transactionRepository.findTopByAppointmentIdOrderByOccurredAtDesc(1L)).thenReturn(Optional.of(tx));

        appointmentService.cancel(1L);

        verify(appointmentRepository).save(argThat(appt -> {
            BigDecimal expected = new BigDecimal("30.00");
            assertEquals(0, expected.compareTo(appt.getPenalty()), "Penalty should be 10% of 300 = 30");
            assertEquals("CANCELLED", appt.getStatus());
            return true;
        }));
    }

    @Test
    void testCancel_withinDeadline_penaltyCappedAt50() {
        // Appointment starting in 2 hours — order amount 1000 → 10% = 100, capped at 50
        existingAppointment.setStartTime(LocalDateTime.now().plusHours(2));
        existingAppointment.setEndTime(LocalDateTime.now().plusHours(3));
        when(appointmentRepository.findById(1L)).thenReturn(Optional.of(existingAppointment));
        when(appointmentRepository.save(any(Appointment.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction tx = new Transaction();
        tx.setAmount(new BigDecimal("1000.00"));
        when(transactionRepository.findTopByAppointmentIdOrderByOccurredAtDesc(1L)).thenReturn(Optional.of(tx));

        appointmentService.cancel(1L);

        verify(appointmentRepository).save(argThat(appt -> {
            BigDecimal expected = new BigDecimal("50.00");
            assertEquals(0, expected.compareTo(appt.getPenalty()), "Penalty must be capped at 50");
            return true;
        }));
    }

    @Test
    void testAutoRelease_releasesPendingAppointmentsBeyondThreshold() {
        Appointment pending1 = new Appointment();
        pending1.setStatus("PENDING");
        Appointment pending2 = new Appointment();
        pending2.setStatus("PENDING");

        when(appointmentRepository.findByStatusAndCreatedAtBefore(eq("PENDING"), any(LocalDateTime.class)))
                .thenReturn(List.of(pending1, pending2));

        appointmentService.autoReleaseUnconfirmedAppointments();

        assertEquals("RELEASED", pending1.getStatus());
        assertEquals("RELEASED", pending2.getStatus());
        verify(appointmentRepository, times(2)).save(any(Appointment.class));
    }
}
