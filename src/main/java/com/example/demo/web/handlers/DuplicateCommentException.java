package com.example.demo.web.handlers;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateCommentException extends RuntimeException {
    public DuplicateCommentException(String message) {
        super(message);
    }
}
