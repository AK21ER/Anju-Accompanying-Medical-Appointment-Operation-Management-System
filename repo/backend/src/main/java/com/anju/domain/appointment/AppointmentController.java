package com.anju.domain.appointment;

import com.anju.aspect.Auditable;
import com.anju.common.Result;
import com.anju.domain.appointment.dto.AppointmentResponse;
import com.anju.domain.appointment.dto.CreateAppointmentRequest;
import com.anju.domain.appointment.dto.RescheduleAppointmentRequest;
import com.anju.domain.appointment.dto.UpdateAppointmentRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/appointment")
@Tag(name = "Appointment", description = "Appointment creation, update, cancellation and listing")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @Auditable(module = "APPOINTMENT", action = "Create appointment")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SCHEDULER','STAFF')")
    @Operation(summary = "Create appointment")
    public ResponseEntity<Result<AppointmentResponse>> create(
            @Valid @RequestBody CreateAppointmentRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        Appointment appointment = appointmentService.create(request, idempotencyKey);
        return ResponseEntity.ok(Result.success("Appointment created.", AppointmentResponse.fromEntity(appointment)));
    }

    @Auditable(module = "APPOINTMENT", action = "Reschedule appointment")
    @PutMapping("/reschedule")
    @PreAuthorize("hasAnyRole('ADMIN','SCHEDULER','STAFF')")
    @Operation(summary = "Reschedule appointment")
    public ResponseEntity<Result<AppointmentResponse>> reschedule(@Valid @RequestBody RescheduleAppointmentRequest request) {
        Appointment appointment = appointmentService.reschedule(request.getAppointmentId(), request);
        return ResponseEntity.ok(Result.success("Appointment rescheduled.", AppointmentResponse.fromEntity(appointment)));
    }

    @Auditable(module = "APPOINTMENT", action = "Update appointment")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SCHEDULER')")
    @Operation(summary = "Update appointment")
    public ResponseEntity<Result<AppointmentResponse>> update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody UpdateAppointmentRequest request) {
        Appointment updated = appointmentService.update(id, request);
        return ResponseEntity.ok(Result.success("Appointment updated.", AppointmentResponse.fromEntity(updated)));
    }

    @Auditable(module = "APPOINTMENT", action = "Cancel appointment")
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','SCHEDULER','STAFF')")
    @Operation(summary = "Cancel appointment")
    public ResponseEntity<Result<Void>> cancel(@PathVariable @Positive Long id) {
        appointmentService.cancel(id);
        return ResponseEntity.ok(Result.success("Appointment cancelled.", null));
    }

    @Auditable(module = "APPOINTMENT", action = "Delete appointment")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SCHEDULER')")
    @Operation(summary = "Delete appointment")
    public ResponseEntity<Result<Void>> delete(@PathVariable @Positive Long id) {
        appointmentService.delete(id);
        return ResponseEntity.ok(Result.success("Appointment deleted.", null));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SCHEDULER','STAFF')")
    @Operation(summary = "Get appointment by id")
    public ResponseEntity<Result<AppointmentResponse>> getById(@PathVariable @Positive Long id) {
        Appointment appointment = appointmentService.getById(id);
        return ResponseEntity.ok(Result.success(AppointmentResponse.fromEntity(appointment)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SCHEDULER','STAFF')")
    @Operation(summary = "List appointments (paginated)")
    public ResponseEntity<Result<List<AppointmentResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<AppointmentResponse> data = appointmentService.list(page, size)
                .stream()
                .map(AppointmentResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(Result.success(data));
    }
}
