package com.anju.config;

import com.anju.domain.auth.User;
import com.anju.domain.auth.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

@Configuration
@Profile("docker")
public class DockerAdminSeedConfig {

    @Bean
    CommandLineRunner dockerAdminSeedRunner(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${anju.seed.admin.username:admin}") String username,
            @Value("${anju.seed.admin.password:Admin1234}") String password,
            @Value("${anju.seed.admin.display-name:System Admin}") String displayName,
            @Value("${anju.seed.admin.role:ADMIN}") String role,
            @Value("${anju.seed.admin.secondary-password:}") String secondaryPassword) {
        return args -> {
            User user = userRepository.findByUsername(username).orElseGet(User::new);
            user.setUsername(username);
            user.setDisplayName(displayName);
            user.setRole(role);
            user.setStatus(1);

            if (!passwordEncoder.matches(password, user.getPassword() == null ? "" : user.getPassword())) {
                user.setPassword(passwordEncoder.encode(password));
            }

            if (StringUtils.hasText(secondaryPassword)) {
                if (!passwordEncoder.matches(secondaryPassword, user.getSecondaryPassword() == null ? "" : user.getSecondaryPassword())) {
                    user.setSecondaryPassword(passwordEncoder.encode(secondaryPassword));
                }
            }

            userRepository.save(user);
        };
    }
}
