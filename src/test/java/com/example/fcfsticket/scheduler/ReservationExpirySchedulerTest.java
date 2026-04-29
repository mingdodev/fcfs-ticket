package com.example.fcfsticket.scheduler;

import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.domain.ReservationStatus;
import com.example.fcfsticket.repository.ReservationRepository;
import com.example.fcfsticket.service.ReservationTxService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationExpirySchedulerTest {

    @Mock
    ReservationRepository reservationRepository;

    @Mock
    ReservationTxService reservationTxService;

    @InjectMocks
    ReservationExpiryScheduler reservationExpiryScheduler;

    @Test
    void 만료된_pending_예약은_tx_service로_취소한다() {
        Reservation expiredReservation = Reservation.builder()
                .id(10L)
                .concertId(1L)
                .userId("user123")
                .status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().minusSeconds(1))
                .build();
        given(reservationRepository.findByStatusAndExpiresAtBefore(any(), any()))
                .willReturn(List.of(expiredReservation));

        reservationExpiryScheduler.expireReservations();

        verify(reservationTxService).cancel(10L, 1L);
    }
}
