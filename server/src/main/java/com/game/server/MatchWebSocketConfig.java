package com.game.server;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Configuration
@EnableWebSocket
public class MatchWebSocketConfig implements WebSocketConfigurer {

    private final MatchSocketHandler matchSocketHandler;

    public MatchWebSocketConfig(MatchSocketHandler matchSocketHandler) {
        this.matchSocketHandler = matchSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(matchSocketHandler, "/ws/match")
            .addInterceptors(new MatchHandshakeInterceptor())
            .setAllowedOrigins("*");
    }

    static final class MatchHandshakeInterceptor implements HandshakeInterceptor {
        @Override
        public boolean beforeHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes
        ) {
            if (request instanceof ServletServerHttpRequest servletRequest) {
                HttpServletRequest r = servletRequest.getServletRequest();
                String mid = r.getParameter("matchId");
                String seat = r.getParameter("seat");
                attributes.put(MatchSocketHandler.ATTR_MATCH_ID, mid != null ? mid : "demo");
                attributes.put(MatchSocketHandler.ATTR_SEAT, seat != null ? seat : "0");
            } else {
                attributes.put(MatchSocketHandler.ATTR_MATCH_ID, "demo");
                attributes.put(MatchSocketHandler.ATTR_SEAT, "0");
            }
            return true;
        }

        @Override
        public void afterHandshake(
            @NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            Exception ex
        ) {
            // no-op
        }
    }
}
