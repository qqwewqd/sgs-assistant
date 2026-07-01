package com.sanguosha.assistant.vo;

import lombok.Data;

@Data
public class RoomActionRequest {
    private Long generalId;
    private Long targetUserId;
    private Long crownPrinceUserId;
    private String faction;
    private Integer currentHp;
    private Integer maxHp;
    private Integer currentArmor;
    private Integer maxArmor;
    private String markerName;
    private Integer markerCount;
    private Boolean chained;
    private Boolean turnedOver;
}
