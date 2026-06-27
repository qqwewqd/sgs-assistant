package com.sanguosha.assistant.store;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LocalRoomStore {
    private final Map<String, StoredRoom> rooms = new ConcurrentHashMap<>();

    public RoomState get(String roomCode) {
        StoredRoom storedRoom = rooms.get(roomCode);
        if (storedRoom == null) {
            return null;
        }
        if (storedRoom.expiresAt().isBefore(Instant.now())) {
            rooms.remove(roomCode, storedRoom);
            return null;
        }
        return storedRoom.room();
    }

    public void put(String roomCode, RoomState room, Duration ttl) {
        rooms.put(roomCode, new StoredRoom(room, Instant.now().plus(ttl)));
    }

    public boolean exists(String roomCode) {
        return get(roomCode) != null;
    }

    public boolean delete(String roomCode) {
        return rooms.remove(roomCode) != null;
    }

    public List<RoomState> list() {
        Instant now = Instant.now();
        rooms.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        return rooms.values().stream()
                .map(StoredRoom::room)
                .sorted(Comparator.comparing(RoomState::getUpdatedAt).reversed())
                .toList();
    }

    private record StoredRoom(RoomState room, Instant expiresAt) {
    }
}
