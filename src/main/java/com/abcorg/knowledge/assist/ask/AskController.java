package com.abcorg.knowledge.assist.ask;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AskController {

    private static final Logger log = LoggerFactory.getLogger(AskController.class);

    private final AskService service;

    public AskController(AskService service) {
        this.service = service;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        AskResponse response = service.ask(request.question());
        // Question length, never the question text, at INFO: questions can contain secrets.
        log.info("ask: chars={} sources={} promptTokens={} completionTokens={} totalMs={}",
                request.question().length(), response.sources().size(), response.usage().promptTokens(),
                response.usage().completionTokens(), response.timings().totalMs());
        return response;
    }

    @PostMapping("/search")
    public SearchResponse search(@Valid @RequestBody AskRequest request) {
        return service.search(request.question());
    }
}
