package com.example.fcfsticket.service;

import com.example.fcfsticket.client.PaymentClient;
import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.dto.ReservationRequest;
import com.example.fcfsticket.exception.SoldOutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final TicketInventoryService ticketInventoryService;
    private final ReservationTxService reservationTxService;
    private final PaymentClient paymentClient;

    public Reservation reserve(ReservationRequest request) {
        // 1. Redis에서 선착순 판정 (재고 선점)
        if (!ticketInventoryService.reserveIfAvailable(request.getConcertId())) {
            throw new SoldOutException("잔여 티켓이 없습니다.");
        }

        // 2. DB에 예약 생성 (PENDING 상태)
        Reservation reservation = reservationTxService.createPending(request);

        try {
            // 3. 결제 요청
            paymentClient.requestPayment(reservation.getConcertId(), reservation.getUserId());
        } catch (Exception e) {
            try {
                // 4. 보상 트랜잭션: Redis 복구 + DB 취소
                ticketInventoryService.restoreTicket(request.getConcertId());
                reservationTxService.cancel(reservation.getId(), reservation.getConcertId());
            } catch (Exception compensationEx) {
                log.error("compensation failed: reservationId={}, reason={}", reservation.getId(), compensationEx.getMessage());
            }
            throw e;
        }

        // 5. 확정 (PENDING → CONFIRMED)
        return reservationTxService.confirm(reservation.getId());
    }
}
