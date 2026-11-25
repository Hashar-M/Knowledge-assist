package com.abcorg.knowledge.assist.ask;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /ask and POST /search. Chapter 12 adds optional filters. */
public record AskRequest(@NotBlank @Size(max = 1000) String question) {
}
