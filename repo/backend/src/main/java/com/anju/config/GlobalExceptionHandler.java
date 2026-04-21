package com.anju.config;

import com.anju.common.BusinessException;
import com.anju.common.Result;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusinessException(BusinessException ex) {
        log.warn("BusinessException: {}", ex.getMessage());
        Result<Void> result = Result.fail(ex.getCode(), ex.getMessage());
        return ResponseEntity.status(resolveStatus(ex.getCode())).body(result);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));

        if (message.isBlank()) {
            message = "Request parameter validation failed.";
        }
        Result<Void> result = Result.fail(4001, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        String msg = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining("; "));
        Result<Void> result = Result.fail(4002, msg.isBlank() ? "Validation failed." : msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException ex) {
        String targetType = ex.getRequiredType() == null ? "required type" : ex.getRequiredType().getSimpleName();
        String message = String.format("Parameter '%s' must be a valid %s.", ex.getName(), targetType);
        Result<Void> result = Result.fail(4003, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Result<Void>> handleMissingRequestHeaderException(MissingRequestHeaderException ex) {
        String message = String.format("Required header '%s' is missing.", ex.getMessage());
        Result<Void> result = Result.fail(4004, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        String message = String.format("Required parameter '%s' is missing.", ex.getParameterName());
        Result<Void> result = Result.fail(4004, message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        String detailMessage = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "Malformed JSON data.";
        Result<Void> result = Result.fail(4005, "Invalid request payload: " + detailMessage);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }
    
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleHttpMediaTypeNotSupportedException(HttpMediaTypeNotSupportedException ex) {
        Result<Void> result = Result.fail(4015, "Unsupported Media Type: " + ex.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(result);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Result<Void>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Result<Void> result = Result.fail(4006, "Invalid argument: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Result<Void>> handleIllegalStateException(IllegalStateException ex) {
        Result<Void> result = Result.fail(4009, "Illegal state: " + ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        log.error("Data integrity violation", ex);
        String msg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        Result<Void> result = Result.fail(4007, "Database error: " + msg);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(result);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFoundException(NoResourceFoundException ex) {
        Result<Void> result = Result.fail(4040, "Endpoint not found.");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleHttpRequestMethodNotSupportedException(org.springframework.web.HttpRequestMethodNotSupportedException ex) {
        Result<Void> result = Result.fail(4050, "Method not allowed: " + ex.getMethod());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(result);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException ex) {
        String msg = ex.getMessage();
        Result<Void> result = Result.fail(4030, msg != null ? msg : "Access Denied.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
    }
    
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuthenticationException(org.springframework.security.core.AuthenticationException ex) {
        String msg = ex.getMessage();
        Result<Void> result = Result.fail(4010, msg != null ? msg : "Unauthorized / Invalid Credentials.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleException(Exception ex) {
        log.error("Unhandled system exception", ex);
        String msg = ex.getMessage();
        Result<Void> result = Result.fail(5000, msg != null ? "Internal Server Error: " + msg : "Internal Server Error.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }

    private HttpStatus resolveStatus(int code) {
        if (code >= 5000) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (code >= 4090 && code < 4100) {
            return HttpStatus.CONFLICT;
        }
        if (code >= 4040 && code < 5000) {
            return HttpStatus.NOT_FOUND;
        }
        if (code >= 4030 && code < 4040) {
            return HttpStatus.FORBIDDEN;
        }
        if (code == 4010) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (code >= 4010 && code < 4030) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.BAD_REQUEST;
    }

    private String formatFieldError(FieldError fieldError) {
        String defaultMessage = fieldError.getDefaultMessage();
        if (defaultMessage == null || defaultMessage.isBlank()) {
            return fieldError.getField() + " is invalid";
        }
        return fieldError.getField() + ": " + defaultMessage;
    }
}
