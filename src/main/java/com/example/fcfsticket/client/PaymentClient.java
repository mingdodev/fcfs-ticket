package com.example.fcfsticket.client;

import com.example.fcfsticket.exception.PaymentFailedException;
import com.example.fcfsticket.exception.PaymentUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class PaymentClient {

    private final RestClient restClient;

    public PaymentClient(@Value("${payment.url}") String paymentUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(paymentUrl)
                .build();
    }

    public void requestPayment(Long concertId, String userId) {
        PaymentResponse response;
        try {
            response = restClient.post()
                    .uri("/pay")
                    .body(new PaymentRequest(concertId, userId))
                    .retrieve()
                    .body(PaymentResponse.class);
        } catch (ResourceAccessException e) {
            throw new PaymentUnavailableException();
        }

        if (response == null || "FAIL".equals(response.status())) {
            throw new PaymentFailedException();
        }
    }

    record PaymentRequest(Long concertId, String userId) {}
    record PaymentResponse(String status) {}
}
