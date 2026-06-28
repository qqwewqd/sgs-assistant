package com.sanguosha.assistant.service;

import com.sanguosha.assistant.security.AuthUser;
import com.sanguosha.assistant.vo.RoomView;
import java.util.List;

public interface GameRoomService {
    RoomView createRoom(AuthUser user);

    RoomView joinRoom(String roomCode, AuthUser user);

    RoomView getRoom(String roomCode, AuthUser user);

    RoomView startGame(String roomCode, AuthUser user);

    RoomView chooseGeneral(String roomCode, Long generalId, AuthUser user);

    RoomView lockGeneral(String roomCode, AuthUser user);

    RoomView revealGeneral(String roomCode, AuthUser user);

    RoomView drawExtraGeneral(String roomCode, String faction, AuthUser user);

    RoomView revealExtraGeneral(String roomCode, int index, AuthUser user);

    RoomView removeExtraGeneral(String roomCode, int index, AuthUser user);

    RoomView markDead(String roomCode, Long targetUserId, AuthUser user);

    RoomView updateVitals(String roomCode, Integer currentHp, Integer maxHp, Integer currentArmor, Integer maxArmor, AuthUser user);

    RoomView updateMarker(String roomCode, Long targetUserId, String markerName, Integer markerCount, AuthUser user);

    RoomView updateOwnStatus(String roomCode, Boolean chained, Boolean turnedOver, AuthUser user);

    RoomView restart(String roomCode, AuthUser user);

    RoomView leave(String roomCode, AuthUser user);

    void dissolve(String roomCode, AuthUser user);

    List<RoomView.GeneralCard> safeLordGenerals(String roomCode, String keyword, AuthUser user);

    void touch(String roomCode);

    void setOnline(String roomCode, Long userId, boolean online);
}
