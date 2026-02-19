package com.confessionverse.backend.service;

import com.confessionverse.backend.config.PremiumGatesProperties;
import com.confessionverse.backend.exception.PremiumRoomRequiredException;
import com.confessionverse.backend.mapper.EntityDtoMapper;
import com.confessionverse.backend.model.ChatRoomType;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatRoomMembershipRepository;
import com.confessionverse.backend.repository.ChatRoomRepository;
import com.confessionverse.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomServicePremiumGatesTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private EntityDtoMapper mapper;

    @Mock
    private ChatRoomMembershipRepository chatRoomMembershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SubscriptionEntitlementService subscriptionEntitlementService;

    @Mock
    private PremiumGatesProperties premiumGatesProperties;

    @Mock
    private Environment environment;

    @Mock
    private Clock clock;

    private ChatRoomService chatRoomService;

    @BeforeEach
    void setUp() {
        chatRoomService = new ChatRoomService(
                chatRoomRepository,
                chatRoomMembershipRepository,
                mapper,
                userRepository,
                subscriptionEntitlementService,
                premiumGatesProperties,
                environment,
                clock
        );
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
    }

    @Test
    void shouldAllowPremiumRoomWhenGatesDisabled() {
        User user = new User();
        user.setId(1L);
        user.setRole(Role.USER);
        when(premiumGatesProperties.isEnabled()).thenReturn(false);

        assertDoesNotThrow(() -> chatRoomService.enforcePremiumRoomAccess(user, ChatRoomType.HEARTBEAT));
        verifyNoInteractions(subscriptionEntitlementService);
    }

    @Test
    void shouldBlockPremiumRoomWhenGatesEnabledAndUserIsFree() {
        User user = new User();
        user.setId(1L);
        user.setRole(Role.USER);
        when(premiumGatesProperties.isEnabled()).thenReturn(true);
        when(subscriptionEntitlementService.isPro(1L)).thenReturn(false);

        assertThrows(PremiumRoomRequiredException.class,
                () -> chatRoomService.enforcePremiumRoomAccess(user, ChatRoomType.TAROT_DREAMS));
    }

    @Test
    void shouldAllowPremiumRoomWhenGatesEnabledAndUserIsPro() {
        User user = new User();
        user.setId(1L);
        user.setRole(Role.USER);
        when(premiumGatesProperties.isEnabled()).thenReturn(true);
        when(subscriptionEntitlementService.isPro(1L)).thenReturn(true);

        assertDoesNotThrow(() -> chatRoomService.enforcePremiumRoomAccess(user, ChatRoomType.HEARTBEAT));
    }
}
