package com.sanguosha.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("identity_mode_rules")
public class IdentityModeRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long modeId;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
