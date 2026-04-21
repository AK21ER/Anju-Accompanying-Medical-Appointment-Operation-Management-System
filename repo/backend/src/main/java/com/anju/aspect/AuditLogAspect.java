package com.anju.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Aspect
@Component
public class AuditLogAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditLogAspect.class);

    private final SysAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogAspect(SysAuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(auditable)")
    public Object aroundAuditableMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setTraceId(MDC.get("traceId"));
        auditLog.setUsername(resolveOperator(auditable));
        auditLog.setModule(auditable.module());
        auditLog.setAction(auditable.action());
        auditLog.setCreatedAt(LocalDateTime.now());
        auditLog.setRequestPayload(serializeArguments(joinPoint));
        enrichRequestInfo(auditLog);

        try {
            Object result = joinPoint.proceed();
            auditLog.setSuccess(Boolean.TRUE);
            auditLog.setResponsePayload(serialize(result));
            persistAuditLog(auditLog);
            return result;
        } catch (Throwable ex) {
            auditLog.setSuccess(Boolean.FALSE);
            auditLog.setErrorMessage(sanitizeErrorMessage(ex));
            persistAuditLog(auditLog);
            throw ex;
        }
    }

    private void enrichRequestInfo(SysAuditLog auditLog) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return;
        }
        HttpServletRequest request = attrs.getRequest();
        auditLog.setHttpMethod(request.getMethod());
        auditLog.setEndpoint(request.getRequestURI());
        auditLog.setIpAddress(resolveClientIp(request));
        auditLog.setUserAgent(request.getHeader("User-Agent"));
    }

    private String resolveOperator(Auditable auditable) {
        if (StringUtils.hasText(auditable.operator())) {
            return auditable.operator();
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && StringUtils.hasText(authentication.getName())) {
            return authentication.getName();
        }
        return "anonymous";
    }

    private String resolveClientIp(HttpServletRequest request) {
        // X-Forwarded-For and X-Real-IP can be forged by any client; use only the
        // direct socket address for tamper-proof IP attribution in audit logs.
        return request.getRemoteAddr();
    }

    private String sanitizeErrorMessage(Throwable ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return "Unexpected error";
        }
        if (message.length() > 1000) {
            return message.substring(0, 1000);
        }
        return message;
    }

    private String serializeArguments(ProceedingJoinPoint joinPoint) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = methodSignature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();

        List<Object> serializableArgs = new ArrayList<>();
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (!isSerializableArgument(arg)) continue;
                if (i < parameters.length && isSensitiveHeader(parameters[i])) {
                    serializableArgs.add("***REDACTED***");
                    continue;
                }
                serializableArgs.add(arg);
            }
        }
        return serialize(serializableArgs);
    }

    private boolean isSerializableArgument(Object arg) {
        return !(arg instanceof ServletRequest)
                && !(arg instanceof ServletResponse)
                && !(arg instanceof MultipartFile)
                && !(arg instanceof BindingResult);
    }

    private boolean isSensitiveHeader(Parameter parameter) {
        RequestHeader requestHeader = parameter.getAnnotation(RequestHeader.class);
        if (requestHeader == null) return false;
        String name = requestHeader.value().isEmpty() ? requestHeader.name() : requestHeader.value();
        String lower = name.toLowerCase();
        return lower.contains("password") || lower.contains("secret") || lower.contains("token");
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return "\"<unserializable>\"";
        }
    }

    private void persistAuditLog(SysAuditLog auditLog) {
        try {
            auditLogRepository.save(auditLog);
        } catch (Exception ex) {
            log.warn("Failed to persist audit log for action={}", auditLog.getAction());
        }
    }
}
