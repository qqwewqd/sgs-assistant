package com.sanguosha.assistant.vo;

import lombok.Data;

@Data
public class RoomActionRequest {
    private Long generalId;
    private Long targetUserId;
    private String faction;
}
