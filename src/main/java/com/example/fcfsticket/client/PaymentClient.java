package com.example.fcfsticket.client;

import com.example.fcfsticket.exception.PaymentFailedException;
import com.example.fcfsticket.exception.PaymentUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import java.time.Duration;

@Slf4j
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
        log.info("payment request: concertId={}, userId={}", concertId, userId);
        try {
            PaymentResponse response = restClient.post()
                    .uri("/pay")
                    .body(new PaymentRequest(concertId, userId))
                    .retrieve()
                    .body(PaymentResponse.class);

            if (response == null || "FAIL".equals(response.status())) {
                log.warn("payment failed: concertId={}, userId={}, status={}", concertId, userId, response == null ? "null" : response.status());
                throw new PaymentFailedException();
            }
            log.info("payment success: concertId={}, userId={}", concertId, userId);
        } catch (ResourceAccessException e) {
            log.error("payment unavailable: concertId={}, userId={}, error={}", concertId, userId, e.getMessage());
            throw new PaymentUnavailableException();
        }
    }

    record PaymentRequest(Long concertId, String userId) {}
    record PaymentResponse(String status) {}
}