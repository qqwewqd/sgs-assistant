package com.sanguosha.assistant.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanguosha.assistant.security.AuthUser;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final Duration ttl;
    private final ObjectMapper objectMapper;

    public JwtUtil(
            @Value("${sgs.jwt.secret}") String secret,
            @Value("${sgs.jwt.expire-hours}") long expireHours,
            ObjectMapper objectMapper
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = Duration.ofHours(expireHours);
        this.objectMapper = objectMapper;
    }

    public TokenPair createToken(Long id, String username, String role) {
        Instant expiresAt = Instant.now().plus(ttl);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(id));
        payload.put("username", username);
        payload.put("role", role);
        payload.put("exp", expiresAt.getEpochSecond());

        String encodedHeader = base64Url(writeJson(header));
        String encodedPayload = base64Url(writeJson(payload));
        String content = encodedHeader + "." + encodedPayload;
        return new TokenPair(content + "." + sign(content), expiresAt);
    }

    public AuthUser parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String content = parts[0] + "." + parts[1];
            if (!constantTimeEquals(sign(content), parts[2])) {
                return null;
            }
            Map<String, Object> payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]),
                    new TypeReference<>() {
                    }
            );
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.ofEpochSecond(exp).isBefore(Instant.now())) {
                return null;
            }
            Long id = Long.valueOf(String.valueOf(payload.get("sub")));
            String username = String.valueOf(payload.get("username"));
            String role = String.valueOf(payload.get("role"));
            return new AuthUser(id, username, role);
        } catch (Exception ex) {
            return null;
        }
    }

    private byte[] writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot create token", ex);
        }
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return base64Url(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot sign token", ex);
        }
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left.length() != right.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }

    public record TokenPair(String token, Instant expiresAt) {
    }
}
