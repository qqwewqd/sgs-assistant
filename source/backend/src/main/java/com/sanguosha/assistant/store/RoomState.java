package com.sanguosha.assistant.store;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RoomState {
    public static final String WAITING = "WAITING";
    public static final String SELECTING = "SELECTING";
    public static final String PLAYING = "PLAYING";

    private String roomCode;
    private Long ownerUserId;
    private Long identityModeId;
    private String identityModeName;
    private String status = WAITING;
    private List<PlayerState> players = new ArrayList<>();
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
