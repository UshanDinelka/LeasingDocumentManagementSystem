package com.leasingdocument.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationTime;


    // =========================================================
    // REVOKED TOKEN STORE
    //
    // token -> expiry timestamp
    // =========================================================

    private final Map<String, Long> revokedTokens =
            new ConcurrentHashMap<>();


    // =========================================================
    // CONSTRUCTOR
    //
    // JWT configuration is injected from application.properties.
    // Environment variables can override those values later.
    // =========================================================

    public JwtService(
            @Value("${app.jwt.secret}") String secretKey,
            @Value("${app.jwt.expiration-ms}") long expirationTime
    ) {

        if (
                secretKey == null ||
                        secretKey.trim().isEmpty()
        ) {
            throw new IllegalStateException(
                    "JWT secret must not be empty."
            );
        }


        byte[] secretBytes =
                secretKey.getBytes(
                        StandardCharsets.UTF_8
                );


        // HS256 requires at least a 256-bit / 32-byte secret.
        if (secretBytes.length < 32) {

            throw new IllegalStateException(
                    "JWT secret must be at least 32 bytes long."
            );
        }


        if (expirationTime <= 0) {

            throw new IllegalStateException(
                    "JWT expiration time must be greater than zero."
            );
        }


        this.key =
                Keys.hmacShaKeyFor(
                        secretBytes
                );

        this.expirationTime =
                expirationTime;
    }


    // =========================================================
    // GENERATE JWT TOKEN
    // =========================================================

    public String generateToken(
            Long userId,
            String role
    ) {

        Date issuedAt =
                new Date();

        Date expiration =
                new Date(
                        issuedAt.getTime()
                                + expirationTime
                );


        return Jwts.builder()
                .subject(
                        String.valueOf(userId)
                )
                .claim(
                        "role",
                        role
                )
                .issuedAt(
                        issuedAt
                )
                .expiration(
                        expiration
                )
                .signWith(
                        key
                )
                .compact();
    }


    // =========================================================
    // GET CLAIMS
    // =========================================================

    private Claims extractClaims(
            String token
    ) {

        return Jwts.parser()
                .verifyWith(
                        key
                )
                .build()
                .parseSignedClaims(
                        token
                )
                .getPayload();
    }


    // =========================================================
    // GET USER ID
    // =========================================================

    public String extractUserId(
            String token
    ) {

        return extractClaims(
                token
        ).getSubject();
    }


    // =========================================================
    // GET ROLE
    // =========================================================

    public String extractRole(
            String token
    ) {

        return extractClaims(
                token
        ).get(
                "role",
                String.class
        );
    }


    // =========================================================
    // REVOKE TOKEN
    // =========================================================

    public void revokeToken(
            String token
    ) {

        if (
                token == null ||
                        token.isBlank()
        ) {

            return;
        }


        try {

            Claims claims =
                    extractClaims(
                            token
                    );


            Date expiration =
                    claims.getExpiration();


            if (expiration != null) {

                revokedTokens.put(
                        token,
                        expiration.getTime()
                );
            }


        } catch (
                JwtException |
                IllegalArgumentException e
        ) {

            // Invalid tokens do not need to be stored.
        }
    }


    // =========================================================
    // CHECK WHETHER TOKEN IS REVOKED
    // =========================================================

    public boolean isTokenRevoked(
            String token
    ) {

        if (
                token == null ||
                        token.isBlank()
        ) {

            return true;
        }


        Long expiryTime =
                revokedTokens.get(
                        token
                );


        // Token is not in blacklist.
        if (expiryTime == null) {

            return false;
        }


        // Remove expired blacklist entries.
        if (
                expiryTime <
                        System.currentTimeMillis()
        ) {

            revokedTokens.remove(
                    token
            );

            return false;
        }


        return true;
    }


    // =========================================================
    // CHECK WHETHER TOKEN IS VALID
    // =========================================================

    public boolean isTokenValid(
            String token
    ) {

        if (
                token == null ||
                        token.isBlank()
        ) {

            return false;
        }


        try {

            // Logged-out token must not be accepted.
            if (
                    isTokenRevoked(
                            token
                    )
            ) {

                return false;
            }


            Claims claims =
                    extractClaims(
                            token
                    );


            Date expiration =
                    claims.getExpiration();


            return expiration != null &&
                    expiration.after(
                            new Date()
                    );


        } catch (
                JwtException |
                IllegalArgumentException e
        ) {

            return false;
        }
    }
}