package com.example.fcfsticket.service;

import com.example.fcfsticket.client.PaymentClient;
import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.domain.ReservationStatus;
import com.example.fcfsticket.dto.ReservationRequest;
import com.example.fcfsticket.exception.PaymentFailedException;
import com.example.fcfsticket.exception.PaymentUnavailableException;
import com.example.fcfsticket.exception.SoldOutException;
import com.example.fcfsticket.repository.ReservationCompensationStateRepository;
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
    TicketInventoryService ticketInventoryService;

    @Mock
    ReservationTxService reservationTxService;

    @Mock
    PaymentClient paymentClient;

    @Mock
    ReservationCompensationStateRepository compensationStateRepository;

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

        given(ticketInventoryService.reserveIfAvailable(1L, "user123"))
                .willReturn(TicketInventoryService.ReservationResult.SUCCESS);
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

        given(ticketInventoryService.reserveIfAvailable(1L, "user123"))
                .willReturn(TicketInventoryService.ReservationResult.SUCCESS);
        given(reservationTxService.createPending(request)).willReturn(pendingReservation);
        willThrow(new PaymentFailedException()).given(paymentClient).requestPayment(1L, "user123");

        assertThatThrownBy(() -> reservationService.reserve(request))
                .isInstanceOf(PaymentFailedException.class);

        verify(ticketInventoryService).restoreTicket(1L);
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

        given(ticketInventoryService.reserveIfAvailable(1L, "user123"))
                .willReturn(TicketInventoryService.ReservationResult.SUCCESS);
        given(reservationTxService.createPending(request)).willReturn(pendingReservation);
        willThrow(new PaymentUnavailableException()).given(paymentClient).requestPayment(1L, "user123");

        assertThatThrownBy(() -> reservationService.reserve(request))
                .isInstanceOf(PaymentUnavailableException.class);

        verify(ticketInventoryService).restoreTicket(1L);
        verify(reservationTxService).cancel(10L, 1L);
        verify(reservationTxService, never()).confirm(10L);
    }

    @Test
    void 매진된_티켓은_예약할_수_없다() {
        ReservationRequest request = new ReservationRequest(1L, "user123");

        given(ticketInventoryService.reserveIfAvailable(1L, "user123"))
                .willReturn(TicketInventoryService.ReservationResult.SOLD_OUT);

        assertThatThrownBy(() -> reservationService.reserve(request))
                .isInstanceOf(SoldOutException.class)
                .hasMessage("잔여 티켓이 없습니다.");

        verify(reservationTxService, never()).createPending(request);
        verify(compensationStateRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 중복_예약은_할_수_없다() {
        ReservationRequest request = new ReservationRequest(1L, "user123");

        given(ticketInventoryService.reserveIfAvailable(1L, "user123"))
                .willReturn(TicketInventoryService.ReservationResult.DUPLICATE);

        assertThatThrownBy(() -> reservationService.reserve(request))
                .isInstanceOf(SoldOutException.class)
                .hasMessage("이미 예약한 티켓입니다.");

        verify(reservationTxService, never()).createPending(request);
        verify(compensationStateRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
