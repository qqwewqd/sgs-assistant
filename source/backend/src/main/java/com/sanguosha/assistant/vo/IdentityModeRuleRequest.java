package com.sanguosha.assistant.vo;

import lombok.Data;

@Data
public class IdentityModeRuleRequest {
    private Integer playerCount;
    private String identityName;
    private Integer quantity;
    private Boolean isLeader;
    private Boolean identityVisible;
    private Boolean allowLordGeneral;
    private Integer initialHpBonus;
    private Integer maxHpBonus;
    private Integer sortOrder;
}
