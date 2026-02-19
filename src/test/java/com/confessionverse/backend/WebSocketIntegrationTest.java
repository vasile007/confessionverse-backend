package com.confessionverse.backend;

import com.confessionverse.backend.model.ChatRoom;
import com.confessionverse.backend.model.Role;
import com.confessionverse.backend.model.User;
import com.confessionverse.backend.repository.ChatRoomRepository;
import com.confessionverse.backend.repository.UserRepository;
import com.confessionverse.backend.security.JwtUtil;
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

        BlockingQueue<String> blockingQueue = new LinkedBlockingQueue<>();

        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwtToken);

        StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                session.subscribe("/topic/chat.send", new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return Map.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> message = (Map<String, Object>) payload;
                        Object content = message.get("content");
                        if (content instanceof String contentText) {
                            blockingQueue.offer(contentText);
                        }
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
        String received = blockingQueue.poll(10, TimeUnit.SECONDS);

        assertTrue(received != null && received.contains("Salut test!"), "Mesajul nu a fost primit corect.");
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
