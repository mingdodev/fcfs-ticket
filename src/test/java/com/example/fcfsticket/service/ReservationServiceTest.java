package com.example.fcfsticket.service;

import com.example.fcfsticket.client.PaymentClient;
import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.domain.ReservationStatus;
import com.example.fcfsticket.dto.ReservationRequest;
import com.example.fcfsticket.exception.PaymentFailedException;
import com.example.fcfsticket.exception.PaymentUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    ReservationTxService reservationTxService;

    @Mock
    PaymentClient paymentClient;

    @InjectMocks
    ReservationService reservationService;

    @Test
    void 결제까지_성공하면_확정된_예약을_반환한다() {
        ReservationRequest request = new ReservationRequest(1L, "user123");
        Reservation pendingReservation = Reservation.builder()
                .id(10L)
                .concertId(1L)
                .userId("user123")
                .status(ReservationStatus.PENDING)
                .build();
        Reservation confirmedReservation = Reservation.builder()
                .id(10L)
                .concertId(1L)
                .userId("user123")
                .status(ReservationStatus.CONFIRMED)
                .build();

        given(reservationTxService.createPending(request)).willReturn(pendingReservation);
        given(reservationTxService.confirm(10L)).willReturn(confirmedReservation);

        Reservation result = reservationService.reserve(request);

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(paymentClient).requestPayment(1L, "user123");
        verify(reservationTxService).confirm(10L);
    }

    @Test
    void 결제_실패시_보상_트랜잭션으로_예약을_취소한다() {
        ReservationRequest request = new ReservationRequest(1L, "user123");
        Reservation pendingReservation = Reservation.builder()
                .id(10L)
                .concertId(1L)
                .userId("user123")
                .status(ReservationStatus.PENDING)
                .build();
        given(reservationTxService.createPending(request)).willReturn(pendingReservation);
        willThrow(new PaymentFailedException()).given(paymentClient).requestPayment(1L, "user123");

        assertThatThrownBy(() -> reservationService.reserve(request))
                .isInstanceOf(PaymentFailedException.class);

        verify(reservationTxService).cancel(10L, 1L);
        verify(reservationTxService, never()).confirm(10L);
    }

    @Test
    void 결제_서버_불가시에도_보상_트랜잭션으로_예약을_취소한다() {
        ReservationRequest request = new ReservationRequest(1L, "user123");
        Reservation pendingReservation = Reservation.builder()
                .id(10L)
                .concertId(1L)
                .userId("user123")
                .status(ReservationStatus.PENDING)
                .build();
        given(reservationTxService.createPending(request)).willReturn(pendingReservation);
        willThrow(new PaymentUnavailableException()).given(paymentClient).requestPayment(1L, "user123");

        assertThatThrownBy(() -> reservationService.reserve(request))
                .isInstanceOf(PaymentUnavailableException.class);

        verify(reservationTxService).cancel(10L, 1L);
        verify(reservationTxService, never()).confirm(10L);
    }
}
