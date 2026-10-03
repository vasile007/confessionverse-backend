package com.confessionverse.backend.config;

import com.confessionverse.backend.service.RandomChatMatchmakingService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
public class RandomChatDisconnectListener {
    private final RandomChatMatchmakingService matchmakingService;

    public RandomChatDisconnectListener(RandomChatMatchmakingService matchmakingService) {
        this.matchmakingService = matchmakingService;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        if (user != null) matchmakingService.disconnect(user.getName());
    }
}
