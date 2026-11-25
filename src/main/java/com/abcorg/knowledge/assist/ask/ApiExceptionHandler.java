package com.abcorg.knowledge.assist.ask;

import com.abcorg.knowledge.assist.exception.UpstreamUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Extending ResponseEntityExceptionHandler gives validation errors (HTTP 400) as problem+json for free.
 * We add one rule: a failing dependency is a 503 with a generic message. The details go to the log, not the client.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(UpstreamUnavailableException.class)
    public ProblemDetail upstream(UpstreamUnavailableException e) {
        log.error("Upstream failure: {}", e.getMessage(), e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "A dependency (search or the model provider) is unavailable. Try again shortly.");
        problem.setTitle("Upstream unavailable");
        return problem;
    }
}
