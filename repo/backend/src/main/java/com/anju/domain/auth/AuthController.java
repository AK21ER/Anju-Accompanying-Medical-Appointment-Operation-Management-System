package com.anju.domain.auth;

import com.anju.common.Result;
import com.anju.domain.auth.dto.LoginRequest;
import com.anju.domain.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Authentication and account endpoints")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final JwtTokenBlacklistService jwtTokenBlacklistService;

    public AuthController(AuthService authService,
                          JwtService jwtService,
                          JwtTokenBlacklistService jwtTokenBlacklistService) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.jwtTokenBlacklistService = jwtTokenBlacklistService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register user", description = "Creates a new user account with optional role and secondary password.")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        User user = authService.register(request);
        return Result.success(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "role", user.getRole()
        ));
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Validates credentials and returns account identity details.")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        User user = authService.login(request);
        String token = jwtService.generateToken(user);
        return Result.success(Map.of(
                "username", user.getUsername(),
                "role", user.getRole(),
                "tokenType", "Bearer",
                "token", token,
                "expiresInSeconds", jwtService.getExpirationSeconds(),
                "message", "Login success. Send the bearer token in protected requests."
        ));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout", description = "Revokes current bearer token so it cannot be reused.")
    public Result<Map<String, Object>> logout(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);
            jwtTokenBlacklistService.revoke(token, jwtService.extractExpiration(token));
        }
        return Result.success(Map.of("loggedOut", true));
    }

    @GetMapping("/me")
    @Operation(summary = "Current user", description = "Returns current authenticated username.")
    public Result<Map<String, Object>> me(Authentication authentication) {
        return Result.success(Map.of("username", authentication.getName()));
    }

    @PostMapping("/verify-secondary")
    @Operation(summary = "Verify secondary password", description = "Validates X-Secondary-Password for sensitive operations.")
    public Result<Map<String, Object>> verifySecondary(
            Authentication authentication,
            @RequestHeader("X-Secondary-Password") String secondaryPassword) {
        authService.verifySecondaryPassword(authentication.getName(), secondaryPassword);
        return Result.success(Map.of("verified", true));
    }

    @PatchMapping("/users/{username}/role")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update user role (admin only)", description = "Promotes or changes a user's role. Restricted to ADMIN callers.")
    public Result<Map<String, Object>> updateUserRole(
            @PathVariable String username,
            @RequestBody Map<String, String> body) {
        String updatedRole = authService.updateUserRole(username, body.get("role"));
        return Result.success(Map.of("username", username, "role", updatedRole));
    }
}
