package com.sanguosha.assistant.store;

import com.sanguosha.assistant.vo.RoomView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PlayerState {
    private Long userId;
    private String username;
    private boolean owner;
    private boolean online = true;
    private boolean locked;
    private boolean dead;
    private boolean generalRevealed;
    private boolean chained;
    private boolean turnedOver;
    private String identity;
    private Integer currentHp;
    private Integer maxHp;
    private Integer currentArmor;
    private Integer maxArmor;
    private RoomView.GeneralCard selectedGeneral;
    private List<RoomView.MarkerView> markers = new ArrayList<>();
    private List<RoomView.ExtraGeneralCard> extraGenerals = new ArrayList<>();
    private List<RoomView.GeneralCard> generalPool = new ArrayList<>();
    private Instant joinedAt = Instant.now();
}
