package com.example.fcfsticket.service;

import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.domain.ReservationStatus;
import com.example.fcfsticket.dto.ReservationRequest;
import com.example.fcfsticket.exception.SoldOutException;
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
    PaymentProcessingService paymentProcessingService;

    @InjectMocks
    ReservationService reservationService;

    @Test
    void 예약_접수_후_PENDING_상태로_즉시_반환한다() {
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

        Reservation result = reservationService.reserve(request);

        // 즉시 PENDING 상태로 반환
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(result.getId()).isEqualTo(10L);

        // 백그라운드 결제 처리 예약
        verify(paymentProcessingService).processPaymentAsync(10L, request);
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
    }
}
