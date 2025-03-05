package com.aisip.OnO.backend.admin.config;

import com.aisip.OnO.backend.user.entity.User;
import com.aisip.OnO.backend.user.entity.UserType;
import com.aisip.OnO.backend.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AdminConfig {

    @Value("${admin.identifier}")
    private String adminIdentifier;

    @Value("${admin.password}")
    private String adminPassword;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminConfig(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public CommandLineRunner initializeAdminUser() {
        return args -> {
            if (userRepository.findByIdentifier(adminIdentifier).isEmpty()) {
                User adminUser = new User();
                adminUser.setEmail("admin@ono.com");
                adminUser.setName("Admin");
                adminUser.setIdentifier(adminIdentifier);
                adminUser.setPassword(passwordEncoder.encode(adminPassword));
                adminUser.setType(UserType.ADMIN);
                userRepository.save(adminUser);
            }
        };
    }
}
