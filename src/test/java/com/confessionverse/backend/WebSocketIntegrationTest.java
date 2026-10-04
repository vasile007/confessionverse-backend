package com.confessionverse.backend;

import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatRoomRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.JwtUtil;
import com.confessionverse.backend.service.RandomChatMatchmakingService;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private RandomChatMatchmakingService randomChatMatchmakingService;

    @Test
    public void testSendAndReceiveMessage() throws Exception {
        String testEmail = "ws-test@confessionverse.local";
        String testUsername = "ws-test-user";

        User sender = userRepository.findByEmail(testEmail).orElseGet(() -> {
            User user = new User();
            user.setEmail(testEmail);
            user.setUsername(testUsername);
            user.setPasswordHash("test-hash");
            user.setRole(Role.USER);
            return userRepository.save(user);
        });

        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setCreator(sender);
        chatRoom.setUsername(testUsername);
        chatRoom.setParticipants(Set.of(sender));
        chatRoom = chatRoomRepository.save(chatRoom);
        final ChatRoom savedChatRoom = chatRoom;
        final User savedSender = sender;

        String jwtToken = jwtUtil.generateToken(savedSender.getEmail(), savedSender.getEmail(), savedSender.getRole().name());
        String url = "ws://localhost:" + port + "/ws";

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        stompClient.setTaskScheduler(new ConcurrentTaskScheduler());

        BlockingQueue<Map<String, Object>> blockingQueue = new LinkedBlockingQueue<>();

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwtToken);

        StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                session.subscribe("/user/queue/messages", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return Map.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> message = (Map<String, Object>) payload;
                        blockingQueue.offer(message);
                    }
                });

                session.send(
                        "/app/chat.send",
                        new ChatMessage(savedChatRoom.getId().toString(), savedSender.getUsername(), "Salut test!")
                );
            }
        };

        WebSocketHttpHeaders webSocketHeaders = new WebSocketHttpHeaders();
        webSocketHeaders.add("Authorization", "Bearer " + jwtToken);

        CompletableFuture<StompSession> future = stompClient.connectAsync(
                url,
                webSocketHeaders,
                connectHeaders,
                sessionHandler
        );

        future.get(5, TimeUnit.SECONDS);
        Map<String, Object> received = blockingQueue.poll(10, TimeUnit.SECONDS);

        assertTrue(received != null, "Mesajul nu a fost primit corect.");
        assertEquals("Salut test!", received.get("content"));
        assertEquals(savedSender.getId().intValue(), ((Number) received.get("senderId")).intValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> senderPayload = (Map<String, Object>) received.get("sender");
        assertEquals(savedSender.getUsername(), senderPayload.get("username"));
        assertTrue(!senderPayload.containsKey("email"), "WebSocket sender payload must not expose email");
    }

    @Test
    public void randomRoomMessageIsDeliveredToSenderAndOtherMember() throws Exception {
        User first = saveUser("ws-random-first@confessionverse.local", "ws-random-first");
        User second = saveUser("ws-random-second@confessionverse.local", "ws-random-second");
        Long firstRoomId = randomChatMatchmakingService.join(first).getChatRoom().getId();
        Long secondRoomId = randomChatMatchmakingService.join(second).getChatRoom().getId();
        assertEquals(firstRoomId, secondRoomId);

        WebSocketStompClient firstClient = stompClient();
        WebSocketStompClient secondClient = stompClient();
        BlockingQueue<Map<String, Object>> firstMessages = new LinkedBlockingQueue<>();
        BlockingQueue<Map<String, Object>> secondMessages = new LinkedBlockingQueue<>();
        StompSession firstSession = connectAndSubscribe(firstClient, first, firstMessages);
        StompSession secondSession = connectAndSubscribe(secondClient, second, secondMessages);

        firstSession.send("/app/chat.send", new ChatMessage(firstRoomId.toString(), first.getUsername(), "random hello"));

        assertRandomMessage(firstMessages.poll(10, TimeUnit.SECONDS), firstRoomId, first, "random hello");
        assertRandomMessage(secondMessages.poll(10, TimeUnit.SECONDS), firstRoomId, first, "random hello");
        firstSession.disconnect();
        secondSession.disconnect();
        firstClient.stop();
        secondClient.stop();
    }

    private User saveUser(String email, String username) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = new User();
            user.setEmail(email);
            user.setUsername(username);
            user.setPasswordHash("test-hash");
            user.setRole(Role.USER);
            return userRepository.save(user);
        });
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        client.setTaskScheduler(new ConcurrentTaskScheduler());
        return client;
    }

    private StompSession connectAndSubscribe(WebSocketStompClient client,
                                             User user,
                                             BlockingQueue<Map<String, Object>> messages) throws Exception {
        String token = jwtUtil.generateToken(user.getEmail(), user.getEmail(), user.getRole().name());
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token);
        CompletableFuture<Void> subscribed = new CompletableFuture<>();
        StompSession session = client.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                connectHeaders,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                        session.subscribe("/user/queue/messages", new StompFrameHandler() {
                            @Override
                            public Type getPayloadType(StompHeaders headers) {
                                return Map.class;
                            }

                            @Override
                            public void handleFrame(StompHeaders headers, Object payload) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> message = (Map<String, Object>) payload;
                                messages.offer(message);
                            }
                        });
                        subscribed.complete(null);
                    }
                }).get(5, TimeUnit.SECONDS);
        subscribed.get(5, TimeUnit.SECONDS);
        return session;
    }

    private void assertRandomMessage(Map<String, Object> message,
                                     Long roomId,
                                     User sender,
                                     String content) {
        assertTrue(message != null, "Random message was not delivered");
        assertEquals(content, message.get("content"));
        assertEquals(roomId.longValue(), ((Number) message.get("chatRoomId")).longValue());
        assertEquals(sender.getId().longValue(), ((Number) message.get("senderId")).longValue());
    }

    @Data
    static class ChatMessage {
        private String chatRoomId;
        private String sender;
        private String content;

        public ChatMessage() {
        }

        public ChatMessage(String chatRoomId, String sender, String content) {
            this.chatRoomId = chatRoomId;
            this.sender = sender;
            this.content = content;
        }
    }
}
