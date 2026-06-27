package com.sanguosha.assistant.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BootstrapAdminRequest {
    @NotBlank
    private String key;
    @NotBlank
    private String username;
    @NotBlank
    private String password;
}
