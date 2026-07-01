package com.sanguosha.assistant.vo;

import lombok.Data;

@Data
public class IdentityModeRuleRequest {
    private Integer playerCount;
    private String identityName;
    private Integer quantity;
    private Integer generalPoolSize;
    private Boolean isLeader;
    private Boolean identityVisible;
    private Boolean allowLordGeneral;
    private Boolean sameIdentityGeneralVisible;
    private Integer initialHpBonus;
    private Integer maxHpBonus;
    private Integer sortOrder;
}
