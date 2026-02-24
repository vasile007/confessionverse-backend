package com.confessionverse.backend.config;

import com.confessionverse.backend.security.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        StompCommand command = accessor.getCommand();

        if (command == null) {
            return message;
        }

        if (command == StompCommand.CONNECT) {
            String token = getAuthorizationHeader(accessor);
            if (token == null || !token.startsWith("Bearer ")) {
                return null;
            }

            token = token.substring(7); // remove the "Bearer " prefix
            if (!jwtUtil.validateToken(token)) {
                return null;
            }

            try {
                Claims claims = jwtUtil.extractAllClaims(token);
                String email = claims.getSubject();
                String role = claims.get("role", String.class);

                List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                        new SimpleGrantedAuthority("ROLE_" + role)
                );

                Authentication auth = new UsernamePasswordAuthenticationToken(email, null, authorities);
                accessor.setUser(auth);
            } catch (Exception e) {
                System.out.println("JWT invalid in WebSocket: " + e.getMessage());
                return null;
            }
        }

        if ((command == StompCommand.SEND || command == StompCommand.SUBSCRIBE)
                && accessor.getUser() == null) {
            return null;
        }

        return message;
    }

    private String getAuthorizationHeader(StompHeaderAccessor accessor) {
        String token = accessor.getFirstNativeHeader("Authorization");
        if (token != null) {
            return token;
        }

        token = accessor.getFirstNativeHeader("authorization");
        if (token != null) {
            return token;
        }

        Map<String, List<String>> headers = accessor.toNativeHeaderMap();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if ("authorization".equalsIgnoreCase(entry.getKey())
                    && entry.getValue() != null
                    && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }

        return null;
    }
}





