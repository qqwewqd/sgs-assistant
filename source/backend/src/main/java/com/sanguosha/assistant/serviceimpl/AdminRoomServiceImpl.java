package com.sanguosha.assistant.serviceimpl;

import com.sanguosha.assistant.service.AdminRoomService;
import com.sanguosha.assistant.store.LocalRoomStore;
import com.sanguosha.assistant.store.PlayerState;
import com.sanguosha.assistant.store.RoomState;
import com.sanguosha.assistant.vo.AdminRoomVO;
import com.sanguosha.assistant.vo.AppException;
import com.sanguosha.assistant.vo.PageVO;
import com.sanguosha.assistant.vo.ResultCode;
import com.sanguosha.assistant.websocket.RoomSessionManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminRoomServiceImpl implements AdminRoomService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final LocalRoomStore roomStore;
    private final RoomSessionManager roomSessionManager;

    @Override
    public PageVO<AdminRoomVO> listRooms(Integer page, Integer pageSize) {
        List<AdminRoomVO> rooms = roomStore.list().stream()
                .map(this::toVO)
                .toList();
        long current = normalizePage(page);
        long size = normalizePageSize(pageSize);
        long total = rooms.size();
        int fromIndex = (int) Math.min((current - 1) * size, total);
        int toIndex = (int) Math.min(fromIndex + size, total);
        return PageVO.of(rooms.subList(fromIndex, toIndex), total, current, size);
    }

    @Override
    public void dissolveRoom(String roomCode) {
        if (!isRoomCode(roomCode) || !roomStore.delete(roomCode)) {
            throw new AppException(ResultCode.ROOM_NOT_FOUND);
        }
        roomSessionManager.broadcastRoomUpdated(roomCode);
    }

    private boolean isRoomCode(String roomCode) {
        return roomCode != null && roomCode.matches("\\d{4,5}");
    }

    private long normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private AdminRoomVO toVO(RoomState room) {
        AdminRoomVO vo = new AdminRoomVO();
        vo.setRoomCode(room.getRoomCode());
        vo.setStatus(room.getStatus());
        vo.setOwnerUserId(room.getOwnerUserId());
        vo.setIdentityModeId(room.getIdentityModeId());
        vo.setIdentityModeName(room.getIdentityModeName());
        vo.setPlayerCount(room.getPlayers().size());
        vo.setOnlineCount((int) room.getPlayers().stream().filter(PlayerState::isOnline).count());
        vo.setOfflineCount(vo.getPlayerCount() - vo.getOnlineCount());
        vo.setLockedCount((int) room.getPlayers().stream().filter(PlayerState::isLocked).count());
        vo.setCreatedAt(room.getCreatedAt());
        vo.setUpdatedAt(room.getUpdatedAt());

        for (PlayerState player : room.getPlayers()) {
            AdminRoomVO.Player item = new AdminRoomVO.Player();
            item.setUserId(player.getUserId());
            item.setUsername(player.getUsername());
            item.setOwner(player.isOwner());
            item.setOnline(player.isOnline());
            item.setLocked(player.isLocked());
            item.setDead(player.isDead());
            item.setIdentity(player.getIdentity());
            item.setSelectedGeneralName(player.getSelectedGeneral() == null ? null : player.getSelectedGeneral().getName());
            vo.getPlayers().add(item);
            if (player.getUserId().equals(room.getOwnerUserId())) {
                vo.setOwnerUsername(player.getUsername());
            }
        }
        return vo;
    }
}
