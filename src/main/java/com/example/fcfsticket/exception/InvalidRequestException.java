package com.example.fcfsticket.exception;

public class InvalidRequestException extends RuntimeException {
    public InvalidRequestException() {
        super("잘못된 요청입니다.");
    }

    public InvalidRequestException(String message) {
        super(message);
    }
}
