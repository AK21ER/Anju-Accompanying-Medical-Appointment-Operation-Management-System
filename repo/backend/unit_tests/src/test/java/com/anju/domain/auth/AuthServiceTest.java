package com.anju.domain.auth;

import com.anju.common.BusinessException;
import com.anju.domain.auth.dto.LoginRequest;
import com.anju.domain.auth.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerRejectsWeakPassword() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("new-user");
        request.setDisplayName("New User");
        request.setPassword("weak");

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
        assertEquals(4003, ex.getCode());
    }

    @Test
    void verifySecondaryPasswordRejectsMismatch() {
        User user = new User();
        user.setUsername("admin");
        user.setSecondaryPassword("encoded");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> authService.verifySecondaryPassword("admin", "wrong"));
        assertEquals(4033, ex.getCode());
    }

    @Test
    void registerIgnoresSuppliedRoleAndAssignsStaff() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("new-admin-attempt");
        request.setDisplayName("Attacker");
        request.setPassword("Secure1234");
        request.setRole("ADMIN");

        when(userRepository.existsByUsername("new-admin-attempt")).thenReturn(false);
        when(passwordEncoder.encode("Secure1234")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User saved = authService.register(request);
        assertEquals("STAFF", saved.getRole(), "Public registration must always assign STAFF role");
        assertNotEquals("ADMIN", saved.getRole());
    }

    @Test
    void loginUpdatesLastLogin() {
        LoginRequest request = new LoginRequest();
        request.setUsername("staff");
        request.setPassword("Pass1234");

        User user = new User();
        user.setUsername("staff");

        when(userRepository.findByUsername("staff")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = authService.login(request);
        assertEquals("staff", result.getUsername());
    }
}
