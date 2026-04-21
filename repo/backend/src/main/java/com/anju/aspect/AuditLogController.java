package com.anju.aspect;

import com.anju.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/audit")
@Tag(name = "Audit", description = "Audit log query and export endpoints")
public class AuditLogController {

    private final SysAuditLogRepository auditLogRepository;

    public AuditLogController(SysAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    @Operation(summary = "Query audit logs", description = "Paginated audit log query with optional module and date-range filters.")
    public Result<Page<SysAuditLog>> queryLogs(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        LocalDateTime fromDt = from != null ? from.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime toDt = to != null ? to.plusDays(1).atStartOfDay() : LocalDateTime.now().plusDays(1);

        Page<SysAuditLog> result;
        if (StringUtils.hasText(module)) {
            result = auditLogRepository.findByModuleAndCreatedAtBetween(
                    module.trim().toUpperCase(), fromDt, toDt, pageable);
        } else {
            result = auditLogRepository.findByCreatedAtBetween(fromDt, toDt, pageable);
        }
        return Result.success(result);
    }

    @GetMapping(value = "/logs/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    @Operation(summary = "Export audit logs as CSV")
    public ResponseEntity<String> exportLogs(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Pageable pageable = PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "createdAt"));

        LocalDateTime fromDt = from != null ? from.atStartOfDay() : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime toDt = to != null ? to.plusDays(1).atStartOfDay() : LocalDateTime.now().plusDays(1);

        List<SysAuditLog> logs;
        if (StringUtils.hasText(module)) {
            logs = auditLogRepository.findByModuleAndCreatedAtBetween(
                    module.trim().toUpperCase(), fromDt, toDt, pageable).getContent();
        } else {
            logs = auditLogRepository.findByCreatedAtBetween(fromDt, toDt, pageable).getContent();
        }

        StringBuilder csv = new StringBuilder();
        csv.append("id,traceId,username,module,action,httpMethod,endpoint,ipAddress,success,createdAt\n");
        for (SysAuditLog log : logs) {
            csv.append(csvField(log.getId()))
               .append(",").append(csvField(log.getTraceId()))
               .append(",").append(csvField(log.getUsername()))
               .append(",").append(csvField(log.getModule()))
               .append(",").append(csvField(log.getAction()))
               .append(",").append(csvField(log.getHttpMethod()))
               .append(",").append(csvField(log.getEndpoint()))
               .append(",").append(csvField(log.getIpAddress()))
               .append(",").append(log.getSuccess())
               .append(",").append(log.getCreatedAt())
               .append("\n");
        }

        String filename = "audit-logs" + (from != null ? "-" + from : "") + ".csv";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    private String csvField(Object value) {
        if (value == null) return "";
        String s = value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
