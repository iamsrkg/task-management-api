package com.example.taskmanagement.service;

import com.example.taskmanagement.entity.RefreshToken;
import com.example.taskmanagement.entity.User;
import com.example.taskmanagement.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Refresh tokens with rotation.
 *
 * A refresh token is a random string, not a JWT: it has to be looked up anyway to know whether
 * it was revoked, so there is nothing to gain from signing it. Only its hash is stored.
 *
 * Each token works once. Refreshing revokes it and returns a new one. If a token that was
 * already used shows up again, either the client replayed it or someone stole it, and there is
 * no way to tell which. So every token of that user is revoked and they have to sign in again.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String INVALID = "Invalid or expired refresh token";

    private final RefreshTokenRepository repository;
    private final Duration lifetime;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${application.security.jwt.refresh-expiration}") long lifetimeMs) {
        this.repository = repository;
        this.lifetime = Duration.ofMillis(lifetimeMs);
    }

    /** @return the token to give to the client; it can't be recovered from the database afterwards */
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(user, hash(token), LocalDateTime.now().plus(lifetime)));
        return token;
    }

    /**
     * Uses the token up and returns its owner. The caller then issues the next pair.
     * The revocations must survive the exception, hence noRollbackFor.
     */
    @Transactional(noRollbackFor = BadCredentialsException.class)
    public User consume(String token) {
        RefreshToken stored = repository.findByTokenHash(hash(token))
                .orElseThrow(() -> new BadCredentialsException(INVALID));

        if (stored.isRevoked()) {
            int ended = repository.revokeAllFor(stored.getUser(), LocalDateTime.now());
            log.warn("A used refresh token was presented again for user {}; {} session(s) ended",
                    stored.getUser().getId(), ended);
            throw new BadCredentialsException(INVALID);
        }
        if (stored.isExpired()) {
            throw new BadCredentialsException(INVALID);
        }
        stored.revoke();
        return stored.getUser();
    }

    /** Sign-out. Says nothing about whether the token existed. */
    @Transactional
    public void revoke(String token) {
        repository.findByTokenHash(hash(token)).ifPresent(RefreshToken::revoke);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // every JVM has SHA-256
        }
    }
}
