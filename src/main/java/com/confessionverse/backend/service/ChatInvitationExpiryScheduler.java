package com.confessionverse.backend.service;

import com.confessionverse.backend.config.ChatInvitationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ChatInvitationExpiryScheduler {

    private final ChatInvitationService chatInvitationService;
    private final ChatInvitationProperties chatInvitationProperties;

    public ChatInvitationExpiryScheduler(ChatInvitationService chatInvitationService,
                                         ChatInvitationProperties chatInvitationProperties) {
        this.chatInvitationService = chatInvitationService;
        this.chatInvitationProperties = chatInvitationProperties;
    }

    @Scheduled(fixedDelayString = "#{${chat.invites.cleanup-interval-minutes:10} * 60 * 1000}")
    public void expirePendingInvites() {
        if (chatInvitationProperties.getTtlHours() <= 0) {
            return;
        }
        chatInvitationService.expirePendingInvitations();
    }
}
