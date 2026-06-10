package com.example.ragassistant.service;

import com.example.ragassistant.model.User;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
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
        }
        users.put("admin", new User("admin", passwordEncoder.encode(defaultPassword), "ADMIN"));
        users.put("local-user", new User("local-user", passwordEncoder.encode("local123"), "USER"));
    }

    public User findByUsername(String username) {
        return users.get(username);
    }

    public boolean validateCredentials(String username, String rawPassword) {
        User user = users.get(username);
        return user != null && passwordEncoder.matches(rawPassword, user.getPassword());
    }
}
