package com.sanguosha.assistant.vo;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class IdentityModeRequest {
    private String name;
    private Boolean enabled;
    private List<IdentityModeRuleRequest> rules = new ArrayList<>();
}
