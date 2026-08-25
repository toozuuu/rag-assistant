package com.example.ragassistant.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expiration;

    @Value("${jwt.refresh-expiration:2592000000}")
    private long refreshExpiration;

    private SecretKey cachedSigningKey;

    private record RefreshTokenData(String username, long expiresAt) {}
    private final Map<String, RefreshTokenData> refreshTokens = new ConcurrentHashMap<>();

    @PostConstruct
    public void initKey() {
        this.cachedSigningKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey getSigningKey() {
        if (cachedSigningKey == null) {
            cachedSigningKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        return cachedSigningKey;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token).getBody();
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public String generateToken(String username) {
        return Jwts.builder().setSubject(username).setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256).compact();
    }

    public String generateRefreshToken(String username) {
        evictExpiredRefreshTokens();
        String refreshToken = UUID.randomUUID().toString() + "-" + System.currentTimeMillis();
        refreshTokens.put(refreshToken, new RefreshTokenData(username, System.currentTimeMillis() + refreshExpiration));
        return refreshToken;
    }

    public String refreshAccessToken(String refreshToken) {
        evictExpiredRefreshTokens();
        RefreshTokenData data = refreshTokens.get(refreshToken);
        if (data == null || data.expiresAt() < System.currentTimeMillis()) {
            if (data != null) {
                refreshTokens.remove(refreshToken);
            }
            return null;
        }
        return generateToken(data.username());
    }

    public void revokeRefreshToken(String refreshToken) {
        if (refreshToken != null) {
            refreshTokens.remove(refreshToken);
        }
    }

    private void evictExpiredRefreshTokens() {
        long now = System.currentTimeMillis();
        refreshTokens.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    public boolean validateToken(String token, String username) {
        try {
            final String extractedUsername = extractUsername(token);
            return (extractedUsername.equals(username) && !isTokenExpired(token));
        } catch (Exception e) {
            return false;
        }
    }
}