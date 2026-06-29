package com.sanguosha.assistant.vo;

import com.sanguosha.assistant.entity.IdentityMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class IdentityModeVO {
    private Long id;
    private String name;
    private Boolean enabled;
    private Boolean builtin;
    private LocalDateTime createdAt;
    private List<Integer> playerCounts = new ArrayList<>();
    private List<IdentityModeRuleVO> rules = new ArrayList<>();

    public static IdentityModeVO from(IdentityMode mode, List<IdentityModeRuleVO> rules) {
        IdentityModeVO vo = new IdentityModeVO();
        vo.setId(mode.getId());
        vo.setName(mode.getName());
        vo.setEnabled(mode.getEnabled());
        vo.setBuiltin(mode.getBuiltin());
        vo.setCreatedAt(mode.getCreatedAt());
        vo.setRules(rules == null ? new ArrayList<>() : rules);
        vo.setPlayerCounts(vo.getRules().stream()
                .map(IdentityModeRuleVO::getPlayerCount)
                .distinct()
                .sorted()
                .toList());
        return vo;
    }
}
