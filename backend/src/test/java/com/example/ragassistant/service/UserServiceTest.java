package com.example.ragassistant.service;

import com.example.ragassistant.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class UserServiceTest {

    private UserService userService;

    @BeforeEach
    void setUp() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(passwordEncoder);
        userService.init();
    }

    @Test
    void findByUsername_existingUser() {
        User user = userService.findByUsername("admin");
        assertNotNull(user);
        assertEquals("admin", user.getUsername());
        assertEquals("ADMIN", user.getRole());
    }

    @Test
    void findByUsername_nonExistingUser() {
        assertNull(userService.findByUsername("nonexistent"));
    }

    @Test
    void validateCredentials_valid() {
        assertTrue(userService.validateCredentials("admin", "admin123"));
    }

    @Test
    void validateCredentials_invalidPassword() {
        assertFalse(userService.validateCredentials("admin", "wrongpassword"));
    }

    @Test
    void validateCredentials_nonExistingUser() {
        assertFalse(userService.validateCredentials("nonexistent", "anypassword"));
    }
}
