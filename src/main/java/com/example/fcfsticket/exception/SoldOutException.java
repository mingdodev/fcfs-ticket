package com.example.fcfsticket.exception;

public class SoldOutException extends RuntimeException {
    public SoldOutException() {
        super("더 이상 예매 가능한 티켓이 없습니다.");
    }

    public SoldOutException(String message) {
        super(message);
    }
}
