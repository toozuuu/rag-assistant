package com.example.ragassistant.service;

import com.example.ragassistant.model.User;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class UserService {

    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final PasswordEncoder passwordEncoder;

    public UserService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        String defaultPassword = System.getenv("DEFAULT_USER_PASSWORD");
        if (defaultPassword == null || defaultPassword.isBlank()) {
            defaultPassword = "admin123";
            log.warn("SECURITY: DEFAULT_USER_PASSWORD env var is not set. " +
                    "The 'admin' account is using a well-known fallback password. " +
                    "Set DEFAULT_USER_PASSWORD in production.");
        }
        users.put("admin", new User("admin", passwordEncoder.encode(defaultPassword), "ADMIN"));

        String localPassword = System.getenv("LOCAL_USER_PASSWORD");
        if (localPassword == null || localPassword.isBlank()) {
            localPassword = "local123";
            log.warn("SECURITY: LOCAL_USER_PASSWORD env var is not set. " +
                    "The 'local-user' account is using a well-known fallback password. " +
                    "Set LOCAL_USER_PASSWORD in production.");
        }
        users.put("local-user", new User("local-user", passwordEncoder.encode(localPassword), "USER"));
    }

    public User findByUsername(String username) {
        return users.get(username);
    }

    public boolean validateCredentials(String username, String rawPassword) {
        User user = users.get(username);
        return user != null && passwordEncoder.matches(rawPassword, user.getPassword());
    }
}
