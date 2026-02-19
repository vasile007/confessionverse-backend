package com.confessionverse.backend.service;

import com.confessionverse.backend.ai.ContentFilter;
import com.confessionverse.backend.dto.requestDTO.AiRequestDto;
import com.confessionverse.backend.dto.responseDTO.AiResponseDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Service
public class AiService {

    private final ContentFilter contentFilter;
    private final RestClient restClient;

    private static final String SYSTEM_PROMPT =
            "You are a professional, empathetic AI assistant.\n" +
            "Help the user with kind advice, dream interpretations, and emotional support.\n" +
            "Avoid any vulgar, negative, or toxic language.\n" +
            "Always maintain a polite and positive tone.";

    public AiService(ContentFilter contentFilter, RestClient openAiRestClient) {
        this.contentFilter = contentFilter;
        this.restClient = openAiRestClient;
    }

    public AiResponseDto getAiReply(AiRequestDto request) {
        String userMessage = request.getMessage();

        if (userMessage == null || userMessage.trim().isEmpty()) {
            return new AiResponseDto("Please enter a message to receive a response.");
        }

        if (contentFilter.containsToxicWords(userMessage)) {
            return new AiResponseDto("Please use respectful and appropriate language.");
        }

        ChatRequest chatRequest = new ChatRequest(
                "gpt-4",
                List.of(
                        new Message("system", SYSTEM_PROMPT),
                        new Message("user", userMessage)
                )
        );

        try {
            ChatResponse chatResponse = restClient.post()
                    .uri("/chat/completions")
                    .body(chatRequest)
                    .retrieve()
                    .body(ChatResponse.class);

            if (chatResponse == null || chatResponse.getChoices() == null || chatResponse.getChoices().isEmpty()) {
                return new AiResponseDto("Sorry, I couldn't generate a response. Please try again later.");
            }

            String aiReply = chatResponse.getChoices().get(0).getMessage().getContent();

            if (contentFilter.containsToxicWords(aiReply)) {
                return new AiResponseDto("Sorry, I couldn't generate an appropriate response. Please try again later.");
            }

            return new AiResponseDto(aiReply);

        } catch (RestClientResponseException e) {
            System.err.println("OpenAI API error: " + e.getResponseBodyAsString());
            return new AiResponseDto("Sorry, I couldn't generate a response. Please try again later.");
        } catch (RestClientException e) {
            return new AiResponseDto("Sorry, I couldn't generate a response. Please try again later.");
        }
    }

    // === Inner Classes (DTOs) ===

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ChatRequest {
        private String model;
        private List<Message> messages;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    public static class ChatResponse {
        private List<Choice> choices;

        @Data
        public static class Choice {
            private Message message;
        }
    }
}


