package com.example.fcfsticket.exception;

public class PaymentUnavailableException extends RuntimeException {
    public PaymentUnavailableException() {
        super("결제 시스템을 일시적으로 사용할 수 없습니다.");
    }

    public PaymentUnavailableException(String message) {
        super(message);
    }
}
