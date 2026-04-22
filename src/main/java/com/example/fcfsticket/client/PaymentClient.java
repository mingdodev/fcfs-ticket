package com.example.fcfsticket.client;

import com.example.fcfsticket.exception.PaymentFailedException;
import com.example.fcfsticket.exception.PaymentUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import java.time.Duration;

@Component
public class PaymentClient {

    private final RestClient restClient;

    public PaymentClient(@Value("${payment.url}") String paymentUrl) {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectionRequestTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(3));

        this.restClient = RestClient.builder()
                .baseUrl(paymentUrl)
                .requestFactory(factory)
                .build();
    }

    public void requestPayment(Long concertId, String userId) {
        try {
            PaymentResponse response = restClient.post()
                    .uri("/pay")
                    .body(new PaymentRequest(concertId, userId))
                    .retrieve()
                    .body(PaymentResponse.class);

            if (response == null || "FAIL".equals(response.status())) {
                throw new PaymentFailedException();
            }
        } catch (ResourceAccessException e) {
            throw new PaymentUnavailableException();
        }
    }

    record PaymentRequest(Long concertId, String userId) {}
    record PaymentResponse(String status) {}
}