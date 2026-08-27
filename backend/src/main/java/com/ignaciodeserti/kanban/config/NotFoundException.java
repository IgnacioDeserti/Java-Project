package com.ignaciodeserti.kanban.config;

/** Thrown when a resource does not exist, or exists but is not owned by the caller. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
