package com.confessionverse.backend.service;

import com.confessionverse.backend.config.LimitsProperties;
import com.confessionverse.backend.exception.FreeLimitReachedException;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatRoomRepository;
import com.confessionverse.backend.repository.MessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;

@Service
public class FreePlanLimitService {

    private final ChatRoomRepository chatRoomRepository;
    private final MessageRepository messageRepository;
    private final SubscriptionEntitlementService subscriptionEntitlementService;
    private final LimitsProperties limitsProperties;
    private final Environment environment;

    @Value("${free.maxMessagesPerDay:100}")
    private long freeMaxMessagesPerDay;

    @Value("${free.maxRoomsPerDay:5}")
    private long freeMaxRoomsPerDay;

    public FreePlanLimitService(ChatRoomRepository chatRoomRepository,
                                MessageRepository messageRepository,
                                SubscriptionEntitlementService subscriptionEntitlementService,
                                LimitsProperties limitsProperties,
                                Environment environment) {
        this.chatRoomRepository = chatRoomRepository;
        this.messageRepository = messageRepository;
        this.subscriptionEntitlementService = subscriptionEntitlementService;
        this.limitsProperties = limitsProperties;
        this.environment = environment;
    }

    public void enforceConversationCreateLimit(User user) {
        if (isDevProfileActive()) {
            return;
        }
        if (!limitsProperties.isEnabled()) {
            return;
        }
        if (user == null || user.getRole() == Role.ADMIN || subscriptionEntitlementService.isPro(user.getId())) {
            return;
        }

        long createdByUser = chatRoomRepository.countByCreator_Id(user.getId());
        if (createdByUser >= freeMaxRoomsPerDay) {
            throw new FreeLimitReachedException("Free plan limit reached. Upgrade to PRO to continue.");
        }
    }

    public void enforceMessageSendLimit(User user) {
        if (isDevProfileActive()) {
            return;
        }
        if (!limitsProperties.isEnabled()) {
            return;
        }
        if (user == null || user.getRole() == Role.ADMIN || subscriptionEntitlementService.isPro(user.getId())) {
            return;
        }

        LocalDateTime dayStart = LocalDateTime.now().minusHours(24);
        long sentLast24h = messageRepository.countBySenderIdAndTimestampAfter(user.getId(), dayStart);
        if (sentLast24h >= freeMaxMessagesPerDay) {
            throw new FreeLimitReachedException("Free plan limit reached. Upgrade to PRO to continue.");
        }
    }

    private boolean isDevProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch("dev"::equalsIgnoreCase);
    }
}
