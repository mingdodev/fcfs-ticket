package com.example.fcfsticket.scheduler;

import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.domain.ReservationStatus;
import com.example.fcfsticket.repository.ReservationRepository;
import com.example.fcfsticket.service.ReservationTxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryScheduler {

    private final ReservationRepository reservationRepository;
    private final ReservationTxService reservationTxService;

    @Scheduled(fixedDelay = 10_000)
    public void expireReservations() {
        List<Reservation> expired = reservationRepository.findByStatusAndExpiresAtBefore(
                ReservationStatus.PENDING, LocalDateTime.now()
        );
        if (expired.isEmpty()) return;

        for (Reservation reservation : expired) {
            reservationTxService.cancel(reservation.getId(), reservation.getConcertId());
        }
    }
}
