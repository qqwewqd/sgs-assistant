package com.sanguosha.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("identity_modes")
public class IdentityMode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Boolean enabled;
    private Boolean builtin;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
