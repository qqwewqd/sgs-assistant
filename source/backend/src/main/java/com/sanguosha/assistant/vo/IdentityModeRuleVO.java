package com.sanguosha.assistant.vo;

import com.sanguosha.assistant.entity.IdentityModeRule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
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
    private Integer generalSelectCount;
    private List<String> allowedFactions;
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
                rule.getGeneralSelectCount(),
                allowedFactions(rule.getAllowedFactions()),
                rule.getIsLeader(),
                rule.getIdentityVisible(),
                rule.getAllowLordGeneral(),
                rule.getSameIdentityGeneralVisible(),
                rule.getInitialHpBonus(),
                rule.getMaxHpBonus(),
                rule.getSortOrder()
        );
    }

    private static List<String> allowedFactions(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return new ObjectMapper().readValue(value, new TypeReference<List<String>>() { });
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
