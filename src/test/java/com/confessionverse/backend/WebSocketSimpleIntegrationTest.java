package com.confessionverse.backend;

import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WebSocketSimpleIntegrationTest {

    @Test
    public void testSendAndReceiveMessage() throws Exception {
        String url = "ws://localhost:8080/ws";

        WebSocketStompClient stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        BlockingQueue<String> blockingQueue = new LinkedBlockingDeque<>();

        // Connect only once
        ListenableFuture<StompSession> futureSession = stompClient.connect(url, new WebSocketHttpHeaders(), new StompSessionHandlerAdapter() {});
        StompSession session = futureSession.get(5, TimeUnit.SECONDS);

        session.subscribe("/topic/messages", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return String.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                blockingQueue.add((String) payload);
            }
        });

        String testMessage = "Salut de la test!";
        session.send("/app/chat", testMessage);

        String received = blockingQueue.poll(3, TimeUnit.SECONDS);
        assertNotNull(received, "Mesajul nu a fost primit");
        assertEquals(testMessage, received);
    }
}
