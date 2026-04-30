package com.example.fcfsticket.service;

import com.example.fcfsticket.domain.ReservationCompensationState;
import com.example.fcfsticket.repository.ReservationCompensationStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationRetryService {

    private final ReservationCompensationStateRepository compensationStateRepository;
    private final TicketInventoryService ticketInventoryService;
    private final ReservationTxService reservationTxService;

    @Value("${ticket.compensation.retry-interval-ms:60000}")
    private long retryIntervalMs;

    @Scheduled(fixedRateString = "${ticket.compensation.retry-interval-ms:60000}")
    public void retryFailedCompensations() {
        // 모든 PENDING/DB_FAILED 상태 처리 (재시도 3회 이내)
        // retryIntervalMs는 스케줄러 간격이므로, 즉시 모두 처리
        List<ReservationCompensationState> retryableStates =
            compensationStateRepository.findAll().stream()
                .filter(state ->
                    (state.getStatus() == ReservationCompensationState.CompensationStatus.PENDING ||
                     state.getStatus() == ReservationCompensationState.CompensationStatus.DB_FAILED) &&
                    state.getRetryCount() < 3
                )
                .toList();

        for (ReservationCompensationState state : retryableStates) {
            retryCompensation(state);
        }
    }

    private void retryCompensation(ReservationCompensationState state) {
        try {
            // Redis 복구는 PENDING일 때만 (DB_FAILED에서는 이미 복구됐으므로 스킵)
            if (state.getStatus() == ReservationCompensationState.CompensationStatus.PENDING) {
                ticketInventoryService.restoreTicket(state.getConcertId());
                state.markRedisSuccess();
            }

            // DB cancel은 REDIS_SUCCESS일 때만
            if (state.getStatus() == ReservationCompensationState.CompensationStatus.REDIS_SUCCESS) {
                reservationTxService.cancel(state.getReservationId(), state.getConcertId());
                state.markCompleted();
            }

            compensationStateRepository.save(state);
        } catch (Exception e) {
            log.error("Compensation retry failed: reservationId={}, retryCount={}, reason={}",
                state.getReservationId(), state.getRetryCount(), e.getMessage());
            state.markDbFailed();
            compensationStateRepository.save(state);
        }
    }
}
