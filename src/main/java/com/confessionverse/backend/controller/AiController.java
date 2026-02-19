package com.confessionverse.backend.controller;

import com.confessionverse.backend.dto.requestDTO.AiRequestDto;
import com.confessionverse.backend.dto.responseDTO.AiResponseDto;
import com.confessionverse.backend.service.AiService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/reply")
    public ResponseEntity<AiResponseDto> getReply(@RequestBody AiRequestDto request) {
        AiResponseDto response = aiService.getAiReply(request);
        return ResponseEntity.ok(response);
    }
}



