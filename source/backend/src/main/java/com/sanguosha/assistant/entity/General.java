package com.sanguosha.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("generals")
public class General {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String imagePath;
    private String faction;
    private Boolean isLord;
    private Boolean startsHidden;
    private Integer initialHp;
    private Integer maxHp;
    private Integer initialArmor;
    private Integer maxArmor;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
