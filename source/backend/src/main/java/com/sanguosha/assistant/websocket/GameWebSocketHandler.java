package com.sanguosha.assistant.websocket;

import com.sanguosha.assistant.security.AuthUser;
import com.sanguosha.assistant.service.GameRoomService;
import com.sanguosha.assistant.util.JwtUtil;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class GameWebSocketHandler extends TextWebSocketHandler {
    private final JwtUtil jwtUtil;
    private final GameRoomService gameRoomService;
    private final RoomSessionManager roomSessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        if (uri == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        var params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        String token = first(params.get("token"));
        String roomCode = first(params.get("roomCode"));
        AuthUser authUser = token == null ? null : jwtUtil.parse(token);
        if (authUser == null || roomCode == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        session.getAttributes().put("roomCode", roomCode);
        session.getAttributes().put("userId", authUser.getId());
        roomSessionManager.register(roomCode, session);
        try {
            gameRoomService.setOnline(roomCode, authUser.getId(), true);
        } catch (RuntimeException ex) {
            roomSessionManager.unregister(roomCode, session);
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        session.sendMessage(new TextMessage("{\"type\":\"CONNECTED\"}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String roomCode = (String) session.getAttributes().get("roomCode");
        if (roomCode == null) {
            return;
        }
        try {
            gameRoomService.touch(roomCode);
        } catch (RuntimeException ex) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        if ("PING".equalsIgnoreCase(message.getPayload())) {
            session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomCode = (String) session.getAttributes().get("roomCode");
        Long userId = (Long) session.getAttributes().get("userId");
        if (roomCode != null) {
            roomSessionManager.unregister(roomCode, session);
        }
        if (roomCode != null && userId != null) {
            try {
                if (!roomSessionManager.hasOpenUserSession(roomCode, userId)) {
                    gameRoomService.setOnline(roomCode, userId, false);
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    private String first(List<String> values) {
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
