package com.example.livetranscription.ws;

import com.example.livetranscription.service.TranscriptionService;
import com.example.livetranscription.service.TranscriptionServiceFactory;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RealtimeWebSocketHandler extends AbstractWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);

    private final TranscriptionServiceFactory factory;
    private final Map<WebSocketSession, TranscriptionService> services = new ConcurrentHashMap<>();

    public RealtimeWebSocketHandler(TranscriptionServiceFactory factory) {
        this.factory = factory;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("New websocket connection established: sessionId={} uri={}", session.getId(), session.getUri());
        TranscriptionService svc = factory.createForSession(session);
        services.put(session, svc);

        // Send open and proxy_open messages to client (synchronized to avoid concurrent writes)
        synchronized (session) {
            session.sendMessage(new TextMessage("{\"type\":\"open\"}"));
            String proxyParams = "{\"sample_rate\":" + factory.getSampleRate() +
                ",\"min_silence_threshold\":" + factory.getMinSilenceThreshold() +
                ",\"max_silence_threshold\":" + factory.getMaxSilenceThreshold() +
                ",\"end_of_turn_threshold\":" + factory.getEndOfTurnThreshold() + "}";
            // send properly JSON-encoded proxy_open with numeric params
            session.sendMessage(new TextMessage("{\"type\":\"proxy_open\",\"params\":{" +
                "\"sample_rate\":" + factory.getSampleRate() +
                ",\"min_silence_threshold\":" + factory.getMinSilenceThreshold() +
                ",\"max_silence_threshold\":" + factory.getMaxSilenceThreshold() +
                ",\"end_of_turn_threshold\":" + factory.getEndOfTurnThreshold() + "}}"));
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        TranscriptionService svc = services.get(session);
        if (svc == null) return;

        if (message instanceof BinaryMessage) {
            BinaryMessage bm = (BinaryMessage) message;
            ByteBuffer payload = bm.getPayload();
            log.debug("Received binary message from session {} ({} bytes)", session.getId(), payload.remaining());
            svc.sendAudio(payload);
        } else if (message instanceof TextMessage) {
            String t = ((TextMessage) message).getPayload();
            log.debug("Received text message from session {}: {}", session.getId(), t);
            // handle control messages if needed
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Transport error on session {}", session != null ? session.getId() : "-", exception);
        try {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage("{\"type\":\"proxy_error\",\"message\":\"" + escape(exception.getMessage()) + "\"}"));
                }
            }
        } catch (IOException e) {
            log.warn("Failed to send proxy_error to client", e);
        }
        closeSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("Session closed: sessionId={} code={} reason={}", session != null ? session.getId() : "-", status != null ? status.getCode() : -1, status != null ? status.getReason() : "");
        TranscriptionService svc = services.remove(session);
        if (svc != null) {
            svc.close();
            log.debug("Closed transcription service for session {}", session != null ? session.getId() : "-");
        }
    }

    private void closeSession(WebSocketSession session) {
        try { if (session != null && session.isOpen()) session.close(); } catch (IOException ignored) {}
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"").replace("\n", "\\n");
    }
}
