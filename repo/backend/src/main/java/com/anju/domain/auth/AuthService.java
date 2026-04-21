package com.anju.domain.auth;

import com.anju.common.BusinessException;
import com.anju.domain.auth.dto.LoginRequest;
import com.anju.domain.auth.dto.RegisterRequest;
import jakarta.transaction.Transactional;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Transactional
public class AuthService {

    private static final Pattern PASSWORD_POLICY = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");
    private static final Set<String> ALLOWED_ROLES = Set.of(
            "STAFF", "ADMIN", "REVIEWER", "SCHEDULER", "FINANCE", "AUDITOR", "OPERATOR"
    );
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    public User register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BusinessException(4090, "Username already exists.");
        }
        validatePassword(request.getPassword());
        if (StringUtils.hasText(request.getSecondaryPassword())) {
            validatePassword(request.getSecondaryPassword());
        }

        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName().trim());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        // Public registration always assigns STAFF role; privileged roles require admin promotion.
        user.setRole("STAFF");
        user.setStatus(1);
        if (StringUtils.hasText(request.getSecondaryPassword())) {
            user.setSecondaryPassword(passwordEncoder.encode(request.getSecondaryPassword()));
        }
        return userRepository.save(user);
    }

    public User login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(4044, "User not found."));
        user.setLastLoginAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    public String updateUserRole(String username, String newRole) {
        if (!StringUtils.hasText(newRole) || !ALLOWED_ROLES.contains(newRole.trim().toUpperCase())) {
            throw new BusinessException(4001, "Invalid role. Allowed: " + ALLOWED_ROLES);
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(4044, "User not found."));
        user.setRole(newRole.trim().toUpperCase());
        userRepository.save(user);
        return user.getRole();
    }

    public void verifySecondaryPassword(String username, String secondaryPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(4044, "User not found."));
        if (!StringUtils.hasText(user.getSecondaryPassword())) {
            throw new BusinessException(4032, "Secondary password is not configured for this account.");
        }
        if (!passwordEncoder.matches(secondaryPassword, user.getSecondaryPassword())) {
            throw new BusinessException(4033, "Invalid secondary password.");
        }
    }

    private void validatePassword(String password) {
        if (!PASSWORD_POLICY.matcher(password).matches()) {
            throw new BusinessException(4003, "Password must be at least 8 chars and contain letters and numbers.");
        }
    }
}
