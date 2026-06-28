package com.sanguosha.assistant.vo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RoomView {
    private String roomCode;
    private String status;
    private Long ownerUserId;
    private boolean owner;
    private boolean canStart;
    private boolean canRestart;
    private Instant updatedAt;
    private List<PlayerView> players = new ArrayList<>();
    private MeView me;

    @Data
    public static class PlayerView {
        private Long userId;
        private String username;
        private boolean owner;
        private boolean online;
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
        private boolean identityVisible;
        private GeneralCard selectedGeneral;
        private List<MarkerView> markers = new ArrayList<>();
        private List<ExtraGeneralCard> extraGenerals = new ArrayList<>();
        private boolean generalVisible;
    }

    @Data
    public static class MeView {
        private Long userId;
        private String username;
        private String identity;
        private boolean locked;
        private boolean dead;
        private boolean generalRevealed;
        private boolean chained;
        private boolean turnedOver;
        private Integer currentHp;
        private Integer maxHp;
        private Integer currentArmor;
        private Integer maxArmor;
        private GeneralCard selectedGeneral;
        private List<MarkerView> markers = new ArrayList<>();
        private List<ExtraGeneralCard> extraGenerals = new ArrayList<>();
        private List<GeneralCard> generalPool = new ArrayList<>();
    }

    @Data
    public static class GeneralCard {
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
    }

    @Data
    public static class ExtraGeneralCard {
        private Long id;
        private String name;
        private String imagePath;
        private String faction;
        private Integer initialHp;
        private Integer maxHp;
        private Integer initialArmor;
        private Integer maxArmor;
        private boolean revealed;
    }

    @Data
    public static class MarkerView {
        private String name;
        private Integer count;
    }
}
