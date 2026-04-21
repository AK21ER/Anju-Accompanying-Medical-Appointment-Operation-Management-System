package com.anju.domain.property;

import com.anju.aspect.Auditable;
import com.anju.common.Result;
import com.anju.domain.auth.AuthService;
import com.anju.domain.property.dto.ComplianceReviewRequest;
import com.anju.domain.property.dto.CreatePropertyRequest;
import com.anju.domain.property.dto.PropertyResponse;
import com.anju.domain.property.dto.UpdatePropertyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/api/properties")
@Tag(name = "Property", description = "Property lifecycle and compliance endpoints")
public class PropertyController {

    private final PropertyService propertyService;
    private final AuthService authService;

    public PropertyController(PropertyService propertyService, AuthService authService) {
        this.propertyService = propertyService;
        this.authService = authService;
    }

    @Auditable(module = "PROPERTY", action = "Create property")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Operation(summary = "Create property")
    public ResponseEntity<Result<PropertyResponse>> create(@Valid @RequestBody CreatePropertyRequest request) {
        Property created = propertyService.create(request);
        return ResponseEntity.ok(Result.success("Property created.", PropertyResponse.fromEntity(created)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','REVIEWER','STAFF')")
    @Operation(summary = "Get property by id")
    public ResponseEntity<Result<PropertyResponse>> getById(@PathVariable @Positive Long id) {
        Property property = propertyService.getById(id);
        return ResponseEntity.ok(Result.success(PropertyResponse.fromEntity(property)));
    }

    @GetMapping("/code/{code}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','REVIEWER','STAFF')")
    @Operation(summary = "Get property by code")
    public ResponseEntity<Result<PropertyResponse>> getByCode(@PathVariable String code) {
        Property property = propertyService.getByCode(code);
        return ResponseEntity.ok(Result.success(PropertyResponse.fromEntity(property)));
    }

    @GetMapping({"", "/"})
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','REVIEWER','STAFF')")
    @Operation(summary = "List properties (paginated)")
    public ResponseEntity<Result<List<PropertyResponse>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String complianceStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<PropertyResponse> data = propertyService.list(status, complianceStatus, page, size)
                .stream()
                .map(PropertyResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(Result.success(data));
    }

    @Auditable(module = "PROPERTY", action = "Update property")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Operation(summary = "Update property")
    public ResponseEntity<Result<PropertyResponse>> update(
            @PathVariable @Positive Long id,
            @Valid @RequestBody UpdatePropertyRequest request) {
        Property updated = propertyService.update(id, request);
        return ResponseEntity.ok(Result.success("Property updated.", PropertyResponse.fromEntity(updated)));
    }

    @Auditable(module = "PROPERTY", action = "Delete property")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete property")
    public ResponseEntity<Result<Void>> delete(@PathVariable @Positive Long id) {
        propertyService.delete(id);
        return ResponseEntity.ok(Result.success("Property deleted.", null));
    }

    @Auditable(module = "PROPERTY", action = "List property")
    @PatchMapping("/{id}/list")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Operation(summary = "Mark property listed")
    public ResponseEntity<Result<PropertyResponse>> listProperty(@PathVariable @Positive Long id) {
        Property listed = propertyService.markListed(id);
        return ResponseEntity.ok(Result.success("Property listed.", PropertyResponse.fromEntity(listed)));
    }

    @Auditable(module = "PROPERTY", action = "Delist property")
    @PatchMapping("/{id}/delist")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    @Operation(summary = "Mark property delisted")
    public ResponseEntity<Result<PropertyResponse>> delistProperty(@PathVariable @Positive Long id) {
        Property delisted = propertyService.markDelisted(id);
        return ResponseEntity.ok(Result.success("Property delisted.", PropertyResponse.fromEntity(delisted)));
    }

    @Auditable(module = "PROPERTY", action = "Compliance review")
    @PatchMapping("/{id}/compliance-review")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    @Operation(summary = "Compliance review")
    public ResponseEntity<Result<PropertyResponse>> reviewCompliance(
            @PathVariable @Positive Long id,
            @Valid @RequestBody ComplianceReviewRequest request,
            @RequestHeader("X-Secondary-Password") String secondaryPassword,
            Authentication authentication) {
        authService.verifySecondaryPassword(authentication.getName(), secondaryPassword);
        Property reviewed = propertyService.reviewCompliance(id, request);
        return ResponseEntity.ok(Result.success("Compliance review updated.", PropertyResponse.fromEntity(reviewed)));
    }
}
