package com.example.fcfsticket.service;

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
    private final PaymentProcessingService paymentProcessingService;

    /**
     * 예약 접수: Redis 선점 + DB PENDING 저장만 수행
     * 결제는 백그라운드에서 비동기 처리
     *
     * @return PENDING 상태의 예약
     */
    public Reservation reserve(ReservationRequest request) {
        // 1. Redis Lua 스크립트: 선착순 선점 (원자적)
        long reserveResult = ticketInventoryService.reserveIfAvailable(
            request.getConcertId(),
            request.getUserId()
        );

        if (reserveResult == TicketInventoryService.ReservationResult.DUPLICATE) {
            throw new SoldOutException("이미 예약한 티켓입니다.");
        }
        if (reserveResult == TicketInventoryService.ReservationResult.SOLD_OUT) {
            throw new SoldOutException("잔여 티켓이 없습니다.");
        }

        // 2. DB PENDING 저장
        Reservation reservation = reservationTxService.createPending(request);
        log.info("Reservation accepted: reservationId={}, concertId={}, userId={}",
            reservation.getId(), request.getConcertId(), request.getUserId());

        // 3. 백그라운드에서 결제 + 상태 변경 처리
        paymentProcessingService.processPaymentAsync(reservation.getId(), request);

        // 4. PENDING 상태로 즉시 반환 (202 Accepted)
        return reservation;
    }
}
