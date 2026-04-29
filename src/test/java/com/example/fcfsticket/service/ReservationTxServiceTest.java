package com.example.fcfsticket.service;

import com.example.fcfsticket.domain.Concert;
import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.domain.ReservationStatus;
import com.example.fcfsticket.dto.ReservationRequest;
import com.example.fcfsticket.exception.ReservationStateException;
import com.example.fcfsticket.repository.ConcertRepository;
import com.example.fcfsticket.repository.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationTxServiceTest {

    @Mock
    ReservationRepository reservationRepository;

    @Mock
    ConcertRepository concertRepository;

    @InjectMocks
    ReservationTxService reservationTxService;

    @Test
    void 티켓이_있으면_pending_예약을_생성한다() {
        ReservationRequest request = new ReservationRequest(1L, "user123");
        Concert concert = Concert.builder().id(1L).name("콘서트 A").remainingTickets(10).build();
        Reservation savedReservation = Reservation.builder()
                .id(10L)
                .concertId(1L)
                .userId("user123")
                .status(ReservationStatus.PENDING)
                .build();

        given(concertRepository.findByIdForUpdate(1L)).willReturn(Optional.of(concert));
        given(reservationRepository.save(any(Reservation.class))).willReturn(savedReservation);

        Reservation result = reservationTxService.createPending(request);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(concert.getRemainingTickets()).isEqualTo(9);
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void confirm은_상태_전이_후_최신_예약을_반환한다() {
        Reservation confirmedReservation = Reservation.builder()
                .id(10L)
                .concertId(1L)
                .userId("user123")
                .status(ReservationStatus.CONFIRMED)
                .build();
        given(reservationRepository.updateStatusIfEquals(10L, ReservationStatus.PENDING, ReservationStatus.CONFIRMED))
                .willReturn(1);
        given(reservationRepository.findById(10L)).willReturn(Optional.of(confirmedReservation));

        Reservation result = reservationTxService.confirm(10L);

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void confirm은_이미_처리된_예약이면_예외를_던진다() {
        given(reservationRepository.updateStatusIfEquals(10L, ReservationStatus.PENDING, ReservationStatus.CONFIRMED))
                .willReturn(0);

        assertThatThrownBy(() -> reservationTxService.confirm(10L))
                .isInstanceOf(ReservationStateException.class)
                .hasMessageContaining("확정할 수 없습니다");
    }

    @Test
    void cancel은_pending일때만_취소하고_티켓을_복구한다() {
        Concert concert = Concert.builder().id(1L).name("콘서트 A").remainingTickets(9).build();
        given(reservationRepository.updateStatusIfEquals(10L, ReservationStatus.PENDING, ReservationStatus.CANCELED))
                .willReturn(1);
        given(concertRepository.findByIdForUpdate(1L)).willReturn(Optional.of(concert));

        boolean canceled = reservationTxService.cancel(10L, 1L);

        assertThat(canceled).isTrue();
        assertThat(concert.getRemainingTickets()).isEqualTo(10);
    }

    @Test
    void cancel은_이미_처리된_예약이면_아무것도_복구하지_않는다() {
        given(reservationRepository.updateStatusIfEquals(10L, ReservationStatus.PENDING, ReservationStatus.CANCELED))
                .willReturn(0);

        boolean canceled = reservationTxService.cancel(10L, 1L);

        assertThat(canceled).isFalse();
        verify(concertRepository, never()).findByIdForUpdate(1L);
    }
}
