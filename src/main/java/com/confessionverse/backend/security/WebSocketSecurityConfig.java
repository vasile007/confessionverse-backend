package com.confessionverse.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
public class WebSocketSecurityConfig {

    private final JwtUtil jwtUtil;

    public WebSocketSecurityConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // security configuration for websocket /messaging
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/ws/**").authenticated()
                        .anyRequest().permitAll()
                )
                .addFilterBefore(new JwtWebSocketAuthenticationFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Custom filter for WebSocket
    public static class JwtWebSocketAuthenticationFilter extends OncePerRequestFilter {

        private final JwtUtil jwtUtil;

        public JwtWebSocketAuthenticationFilter(JwtUtil jwtUtil) {
            this.jwtUtil = jwtUtil;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String authHeader = request.getHeader("Authorization");
            String token = null;
            String username = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
                username = jwtUtil.extractUsername(token);
            }

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                String role = jwtUtil.extractRole(token);

                // Validate token with extracted role
                if (jwtUtil.validateTokenByRole(token, role)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            username,
                            null,
                            jwtUtil.getAuthoritiesFromRole(role)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
            filterChain.doFilter(request, response);
        }
    }
}
