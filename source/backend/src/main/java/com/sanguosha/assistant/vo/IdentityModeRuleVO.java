package com.sanguosha.assistant.vo;

import com.sanguosha.assistant.entity.IdentityModeRule;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentityModeRuleVO {
    private Long id;
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

    public static IdentityModeRuleVO from(IdentityModeRule rule) {
        return new IdentityModeRuleVO(
                rule.getId(),
                rule.getPlayerCount(),
                rule.getIdentityName(),
                rule.getQuantity(),
                rule.getGeneralPoolSize(),
                rule.getIsLeader(),
                rule.getIdentityVisible(),
                rule.getAllowLordGeneral(),
                rule.getSameIdentityGeneralVisible(),
                rule.getInitialHpBonus(),
                rule.getMaxHpBonus(),
                rule.getSortOrder()
        );
    }
}
