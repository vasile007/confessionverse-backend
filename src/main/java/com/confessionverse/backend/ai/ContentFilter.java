package com.confessionverse.backend.ai;

import org.springframework.stereotype.Component;

import java.util.Set;
@Component
public class ContentFilter {

    private final Set<String> bannedWords = Set.of(
            "idiot", "stupid", "dumb", "hate", "kill", "bitch", "fuck", "shit",
            "asshole", "damn", "crap", "bastard", "sucks", "retard", "faggot"
            // Add more as needed
    );

    /**
     * Checks if the given text contains any toxic words.
     * @param text input text
     * @return true if toxic content found, false otherwise
     */
    public boolean containsToxicWords(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return bannedWords.stream().anyMatch(lower::contains);
    }
}
