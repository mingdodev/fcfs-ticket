package com.example.fcfsticket.exception;

public class PaymentFailedException extends RuntimeException {
    public PaymentFailedException() {
        super("결제에 실패했습니다.");
    }

    public PaymentFailedException(String message) {
        super(message);
    }
}
