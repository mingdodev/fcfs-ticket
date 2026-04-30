package com.example.fcfsticket.service;

import com.example.fcfsticket.client.PaymentClient;
import com.example.fcfsticket.domain.ReservationCompensationState;
import com.example.fcfsticket.dto.ReservationRequest;
import com.example.fcfsticket.repository.ReservationCompensationStateRepository;
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
                // 1. 결제 요청
                paymentClient.requestPayment(request.getConcertId(), request.getUserId());

                // 2. 결제 성공 → DB CONFIRMED로 변경
                reservationTxService.confirm(reservationId);
                log.info("Reservation confirmed: reservationId={}", reservationId);

            } catch (Exception e) {
                // 결제 실패 → 보상 처리
                log.error("Payment failed: reservationId={}, reason={}", reservationId, e.getMessage());
                handlePaymentFailure(reservationId, request);
            }
        });
    }

    private void handlePaymentFailure(Long reservationId, ReservationRequest request) {
        // 보상 상태 저장 (CompensationRetryService가 처리)
        ReservationCompensationState compensationState = ReservationCompensationState.builder()
            .reservationId(reservationId)
            .concertId(request.getConcertId())
            .status(ReservationCompensationState.CompensationStatus.PENDING)
            .retryCount(0)
            .createdAt(System.currentTimeMillis())
            .build();

        compensationStateRepository.save(compensationState);
        log.info("Compensation scheduled: reservationId={}", reservationId);
    }
}
