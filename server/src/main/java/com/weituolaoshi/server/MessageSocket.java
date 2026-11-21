package com.weituolaoshi.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Configuration
@EnableWebSocket
public class MessageSocket implements WebSocketConfigurer {
    private final AuthSupport auth;
    private final ObjectMapper json;
    private final Map<String,Set<WebSocketSession>> clients=new ConcurrentHashMap<>();
    public MessageSocket(AuthSupport auth,ObjectMapper json) { this.auth=auth; this.json=json; }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) { registry.addHandler(new TextWebSocketHandler() {
        @Override public void afterConnectionEstablished(WebSocketSession session) { session.getAttributes().put("authenticated",false); }
        @Override protected void handleTextMessage(WebSocketSession session,TextMessage message) throws Exception {
            if(Boolean.TRUE.equals(session.getAttributes().get("authenticated"))) return;
            try {
                Map<?,?> payload=json.readValue(message.getPayload(),Map.class);
                if(!"auth".equals(payload.get("type")) || !(payload.get("token") instanceof String)) { session.close(CloseStatus.POLICY_VIOLATION); return; }
                String user=auth.user("Bearer "+payload.get("token"));
                session.getAttributes().put("user",user); session.getAttributes().put("authenticated",true);
                clients.computeIfAbsent(user,k->ConcurrentHashMap.newKeySet()).add(session);
                session.sendMessage(new TextMessage("{\"type\":\"ready\"}"));
            } catch(Exception ex) { session.close(CloseStatus.POLICY_VIOLATION); }
        }
        @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status) { Object user=session.getAttributes().get("user"); if(user!=null) { Set<WebSocketSession> sessions=clients.get(user); if(sessions!=null) sessions.remove(session); } }
    },"/v1/messages/socket").setAllowedOriginPatterns("*"); }
    public void notifyUser(String user,String conversationId) {
        Set<WebSocketSession> sessions=clients.get(user); if(sessions==null) return;
        for(WebSocketSession session:sessions) try { if(session.isOpen()) session.sendMessage(new TextMessage(json.writeValueAsString(Map.of("type","conversation.updated","conversationId",conversationId)))); } catch(Exception ignored) {}
    }
}
