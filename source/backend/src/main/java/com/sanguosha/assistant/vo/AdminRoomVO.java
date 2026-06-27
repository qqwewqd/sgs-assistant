package com.sanguosha.assistant.vo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class AdminRoomVO {
    private String roomCode;
    private String status;
    private Long ownerUserId;
    private String ownerUsername;
    private int playerCount;
    private int onlineCount;
    private int offlineCount;
    private int lockedCount;
    private Instant createdAt;
    private Instant updatedAt;
    private List<Player> players = new ArrayList<>();

    @Data
    public static class Player {
        private Long userId;
        private String username;
        private boolean owner;
        private boolean online;
        private boolean locked;
        private boolean dead;
        private String identity;
        private String selectedGeneralName;
    }
}
