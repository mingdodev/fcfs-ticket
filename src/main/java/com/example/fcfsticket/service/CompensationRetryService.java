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
        long expireMs = System.currentTimeMillis() - retryIntervalMs;
        List<ReservationCompensationState> retryableStates =
            compensationStateRepository.findRetryableCompensations(expireMs);

        for (ReservationCompensationState state : retryableStates) {
            retryCompensation(state);
        }
    }

    private void retryCompensation(ReservationCompensationState state) {
        try {
            if (state.getStatus() == ReservationCompensationState.CompensationStatus.PENDING) {
                ticketInventoryService.restoreTicket(state.getConcertId());
                state.markRedisSuccess();
            }

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
