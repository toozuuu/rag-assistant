package com.example.ragassistant.service;

import com.example.ragassistant.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86400000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 2592000000L);
    }

    @Test
    void generateAndValidateToken() {
        String token = jwtUtil.generateToken("testuser");
        assertNotNull(token);
        assertTrue(jwtUtil.validateToken(token, "testuser"));
    }

    @Test
    void validateToken_wrongUsername_returnsFalse() {
        String token = jwtUtil.generateToken("testuser");
        assertFalse(jwtUtil.validateToken(token, "wronguser"));
    }

    @Test
    void extractUsername() {
        String token = jwtUtil.generateToken("testuser");
        assertEquals("testuser", jwtUtil.extractUsername(token));
    }

    @Test
    void refreshToken_flow() {
        String refreshToken = jwtUtil.generateRefreshToken("testuser");
        assertNotNull(refreshToken);

        String newAccessToken = jwtUtil.refreshAccessToken(refreshToken);
        assertNotNull(newAccessToken);
        assertEquals("testuser", jwtUtil.extractUsername(newAccessToken));
    }

    @Test
    void refreshToken_invalid_returnsNull() {
        assertNull(jwtUtil.refreshAccessToken("invalid-token"));
    }
}
