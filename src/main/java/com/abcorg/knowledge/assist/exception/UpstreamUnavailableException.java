package com.abcorg.knowledge.assist.exception;

/** Elasticsearch or the model provider could not be reached or failed. Becomes HTTP 503. */
public class UpstreamUnavailableException extends RuntimeException {

    public UpstreamUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
