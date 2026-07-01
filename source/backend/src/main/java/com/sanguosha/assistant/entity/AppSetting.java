package com.sanguosha.assistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("app_settings")
public class AppSetting {
    @TableId(value = "setting_key", type = IdType.INPUT)
    private String settingKey;
    private String settingValue;
    private LocalDateTime updatedAt;
}
