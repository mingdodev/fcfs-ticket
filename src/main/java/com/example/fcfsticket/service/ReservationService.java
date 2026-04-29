package com.example.fcfsticket.service;

import com.example.fcfsticket.client.PaymentClient;
import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.domain.ReservationCompensationState;
import com.example.fcfsticket.dto.ReservationRequest;
import com.example.fcfsticket.exception.SoldOutException;
import com.example.fcfsticket.repository.ReservationCompensationStateRepository;
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
    private final ReservationCompensationStateRepository compensationStateRepository;

    public Reservation reserve(ReservationRequest request) {
        // 1. Redis에서 선착순 판정 (원자적: 중복 확인 + 재고 확인 + 감소 + 사용자 기록)
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

        // 2. DB에 예약 생성 (PENDING 상태)
        Reservation reservation = reservationTxService.createPending(request);

        try {
            // 3. 결제 요청
            paymentClient.requestPayment(reservation.getConcertId(), reservation.getUserId());
        } catch (Exception e) {
            handleCompensation(reservation, request);
            throw e;
        }

        // 4. 확정 (PENDING → CONFIRMED)
        return reservationTxService.confirm(reservation.getId());
    }

    private void handleCompensation(Reservation reservation, ReservationRequest request) {
        // 보상 트랜잭션 상태 저장: Redis 복구는 성공하면 기록, DB 취소 실패는 재시도 대상으로 표시
        ReservationCompensationState compensationState = ReservationCompensationState.builder()
            .reservationId(reservation.getId())
            .concertId(request.getConcertId())
            .status(ReservationCompensationState.CompensationStatus.PENDING)
            .retryCount(0)
            .createdAt(System.currentTimeMillis())
            .build();

        try {
            // Redis 복구
            ticketInventoryService.restoreTicket(request.getConcertId());
            compensationState.markRedisSuccess();
        } catch (Exception redisEx) {
            log.error("Redis restoration failed: reservationId={}, reason={}", reservation.getId(), redisEx.getMessage());
            compensationState.markDbFailed();
            compensationStateRepository.save(compensationState);
            return;
        }

        try {
            // DB 취소
            reservationTxService.cancel(reservation.getId(), request.getConcertId());
            compensationState.markCompleted();
        } catch (Exception dbEx) {
            log.error("DB cancellation failed: reservationId={}, reason={}", reservation.getId(), dbEx.getMessage());
            compensationState.markDbFailed();
        }

        compensationStateRepository.save(compensationState);
    }
}
