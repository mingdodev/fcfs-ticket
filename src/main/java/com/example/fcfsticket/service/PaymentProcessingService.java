package com.example.fcfsticket.service;

import com.example.fcfsticket.client.PaymentClient;
import com.example.fcfsticket.domain.ReservationCompensationState;
import com.example.fcfsticket.dto.ReservationRequest;
import com.example.fcfsticket.repository.ReservationCompensationStateRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessingService {

    private final PaymentClient paymentClient;
    private final ReservationTxService reservationTxService;
    private final TicketInventoryService ticketInventoryService;
    private final ReservationCompensationStateRepository compensationStateRepository;

    /**
     * 백그라운드에서 비동기로 결제 + 상태 변경 처리
     */
    public void processPaymentAsync(Long reservationId, ReservationRequest request) {
        CompletableFuture.runAsync(() -> {
            try {
                // 재시도 + 서킷 브레이커로 결제 처리
                processPaymentWithRetry(request.getConcertId(), request.getUserId());

                // 결제 성공 → DB CONFIRMED로 변경
                reservationTxService.confirm(reservationId);
                log.info("Reservation confirmed: reservationId={}", reservationId);

            } catch (Exception e) {
                // 결제 최종 실패 → 보상 처리
                log.error("Payment failed after retries: reservationId={}, reason={}",
                    reservationId, e.getMessage());
                handlePaymentFailure(reservationId, request);
            }
        });
    }

    /**
     * 결제 요청 (재시도 + 서킷 브레이커)
     * - 최대 3회 재시도 (exponential backoff: 1초 후, 2초 후)
     * - 서킷 브레이커: 50% 실패율 또는 5초 이상 응답 시 차단
     */
    @Retry(name = "payment", fallbackMethod = "paymentFallback")
    @CircuitBreaker(name = "payment", fallbackMethod = "paymentFallback")
    public void processPaymentWithRetry(Long concertId, String userId) {
        log.info("Requesting payment: concertId={}, userId={}", concertId, userId);
        paymentClient.requestPayment(concertId, userId);
    }

    /**
     * 결제 실패 폴백 (서킷 브레이커 또는 모든 재시도 실패 시)
     */
    public void paymentFallback(Long concertId, String userId, Exception ex) {
        log.warn("Payment fallback triggered: concertId={}, userId={}, reason={}",
            concertId, userId, ex.getMessage());
        throw new RuntimeException("Payment processing failed: " + ex.getMessage());
    }

    private void handlePaymentFailure(Long reservationId, ReservationRequest request) {
        handlePaymentFailure(reservationId, request, "PAYMENT_FAILED");
    }

    private void handlePaymentFailure(Long reservationId, ReservationRequest request, String failureReason) {
        // 보상 상태 저장 (CompensationRetryService가 주기적으로 처리)
        ReservationCompensationState compensationState = ReservationCompensationState.builder()
            .reservationId(reservationId)
            .concertId(request.getConcertId())
            .status(ReservationCompensationState.CompensationStatus.PENDING)
            .retryCount(0)
            .createdAt(System.currentTimeMillis())
            .failureReason(failureReason)
            .build();

        compensationStateRepository.save(compensationState);
        log.info("Compensation scheduled: reservationId={}, reason={}", reservationId, failureReason);
    }
}
