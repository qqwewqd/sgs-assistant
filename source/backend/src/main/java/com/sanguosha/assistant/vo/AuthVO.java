package com.sanguosha.assistant.vo;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthVO {
    private String token;
    private Long id;
    private String username;
    private String role;
    private Instant expiresAt;
}
