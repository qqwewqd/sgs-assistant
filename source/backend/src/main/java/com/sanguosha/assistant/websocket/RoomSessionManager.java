package com.sanguosha.assistant.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
@RequiredArgsConstructor
public class RoomSessionManager {
    private final ObjectMapper objectMapper;
    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(String roomCode, WebSocketSession session) {
        sessions.computeIfAbsent(roomCode, key -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(String roomCode, WebSocketSession session) {
        Set<WebSocketSession> roomSessions = sessions.get(roomCode);
        if (roomSessions == null) {
            return;
        }
        roomSessions.remove(session);
        if (roomSessions.isEmpty()) {
            sessions.remove(roomCode);
        }
    }

    public boolean hasOpenUserSession(String roomCode, Long userId) {
        Set<WebSocketSession> roomSessions = sessions.get(roomCode);
        if (roomSessions == null || userId == null) {
            return false;
        }
        return roomSessions.stream()
                .filter(WebSocketSession::isOpen)
                .anyMatch(session -> userId.equals(session.getAttributes().get("userId")));
    }

    public void broadcastRoomUpdated(String roomCode) {
        broadcast(roomCode, Map.of(
                "type", "ROOM_UPDATED",
                "roomCode", roomCode,
                "timestamp", Instant.now().toString()
        ));
    }

    private void broadcast(String roomCode, Object payload) {
        Set<WebSocketSession> roomSessions = sessions.get(roomCode);
        if (roomSessions == null || roomSessions.isEmpty()) {
            return;
        }
        try {
            TextMessage message = new TextMessage(objectMapper.writeValueAsString(payload));
            for (WebSocketSession session : roomSessions) {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
