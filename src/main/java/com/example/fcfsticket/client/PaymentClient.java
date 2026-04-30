package com.example.fcfsticket.client;

import com.example.fcfsticket.exception.PaymentFailedException;
import com.example.fcfsticket.exception.PaymentUnavailableException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class PaymentClient {

    private final RestClient restClient;

    public PaymentClient(
            @Value("${payment.url}") String paymentUrl,
            @Value("${payment.client.max-connections-per-route:200}") int maxConnectionsPerRoute,
            @Value("${payment.client.max-connections-total:400}") int maxConnectionsTotal,
            @Value("${payment.client.connection-request-timeout:PT15S}") Duration connectionRequestTimeout,
            @Value("${payment.client.connect-timeout:PT3S}") Duration connectTimeout,
            @Value("${payment.client.read-timeout:PT10S}") Duration readTimeout
    ) {
        PoolingHttpClientConnectionManager connectionManager =
                PoolingHttpClientConnectionManagerBuilder.create()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.of(connectTimeout))
                                .setSocketTimeout(Timeout.of(readTimeout))
                                .build())
                        .setMaxConnPerRoute(maxConnectionsPerRoute)
                        .setMaxConnTotal(maxConnectionsTotal)
                        .build();

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(connectionRequestTimeout))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .build();

        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
        factory.setConnectionRequestTimeout(connectionRequestTimeout);
        factory.setReadTimeout(readTimeout);

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
                log.warn("Payment rejected by server: concertId={}, userId={}, status={}",
                        concertId, userId, response == null ? "null" : response.status());
                throw new PaymentFailedException("Server rejected: " + (response == null ? "null" : response.status()));
            }
            log.info("Payment accepted: concertId={}, userId={}", concertId, userId);
        } catch (RestClientResponseException e) {
            log.warn("Payment server HTTP error: concertId={}, userId={}, status={}, body={}",
                    concertId, userId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new PaymentFailedException("HTTP " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            Throwable cause = e.getCause();
            log.warn("Payment network error: concertId={}, userId={}, cause={}, message={}",
                    concertId,
                    userId,
                    cause == null ? "unknown" : cause.getClass().getSimpleName(),
                    cause == null ? e.getMessage() : cause.getMessage());
            throw new PaymentUnavailableException();
        }
    }

    record PaymentRequest(Long concertId, String userId) {}
    record PaymentResponse(String status) {}
}
