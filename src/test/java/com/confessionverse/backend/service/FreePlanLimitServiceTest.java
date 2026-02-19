package com.confessionverse.backend.service;

import com.confessionverse.backend.config.LimitsProperties;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatRoomRepository;
import com.confessionverse.backend.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FreePlanLimitServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private SubscriptionEntitlementService subscriptionEntitlementService;

    @Mock
    private LimitsProperties limitsProperties;

    @Mock
    private Environment environment;

    private FreePlanLimitService freePlanLimitService;

    @BeforeEach
    void setUp() {
        freePlanLimitService = new FreePlanLimitService(
                chatRoomRepository,
                messageRepository,
                subscriptionEntitlementService,
                limitsProperties,
                environment
        );
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        when(limitsProperties.isEnabled()).thenReturn(false);
    }

    @Test
    void shouldSkipConversationLimitChecksWhenLimitsDisabled() {
        User user = new User();
        user.setId(1L);
        user.setRole(Role.USER);

        assertDoesNotThrow(() -> freePlanLimitService.enforceConversationCreateLimit(user));
        verifyNoInteractions(chatRoomRepository, messageRepository, subscriptionEntitlementService);
    }

    @Test
    void shouldSkipMessageLimitChecksWhenLimitsDisabled() {
        User user = new User();
        user.setId(1L);
        user.setRole(Role.USER);

        assertDoesNotThrow(() -> freePlanLimitService.enforceMessageSendLimit(user));
        verifyNoInteractions(chatRoomRepository, messageRepository, subscriptionEntitlementService);
    }

    @Test
    void shouldBlockFreeUserWhenMessageLimitReached() {
        User user = new User();
        user.setId(1L);
        user.setRole(Role.USER);

        when(limitsProperties.isEnabled()).thenReturn(true);
        when(subscriptionEntitlementService.isPro(1L)).thenReturn(false);
        ReflectionTestUtils.setField(freePlanLimitService, "freeMaxMessagesPerDay", 1L);
        when(messageRepository.countBySenderIdAndTimestampAfter(org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(1L);

        assertThrows(com.confessionverse.backend.exception.FreeLimitReachedException.class,
                () -> freePlanLimitService.enforceMessageSendLimit(user));
    }

    @Test
    void shouldAllowProUserEvenWhenFreeMessageLimitReached() {
        User user = new User();
        user.setId(1L);
        user.setRole(Role.USER);

        when(limitsProperties.isEnabled()).thenReturn(true);
        when(subscriptionEntitlementService.isPro(1L)).thenReturn(true);
        ReflectionTestUtils.setField(freePlanLimitService, "freeMaxMessagesPerDay", 1L);

        assertDoesNotThrow(() -> freePlanLimitService.enforceMessageSendLimit(user));
    }
}
