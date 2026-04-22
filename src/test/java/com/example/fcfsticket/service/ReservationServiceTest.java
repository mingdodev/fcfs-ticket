package com.example.fcfsticket.service;

import com.example.fcfsticket.client.PaymentClient;
import com.example.fcfsticket.domain.Concert;
import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.dto.ReservationRequest;
import com.example.fcfsticket.exception.PaymentFailedException;
import com.example.fcfsticket.exception.PaymentUnavailableException;
import com.example.fcfsticket.exception.SoldOutException;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    ReservationRepository reservationRepository;

    @Mock
    ConcertRepository concertRepository;

    @Mock
    PaymentClient paymentClient;

    @InjectMocks
    ReservationService reservationService;

    ReservationRequest request = new ReservationRequest(1L, "user123");

    @Test
    void 티켓이_있고_결제가_성공하면_예매가_완료된다() {
        Concert concert = Concert.builder().id(1L).name("콘서트 A").remainingTickets(10).build();
        Reservation saved = Reservation.create(1L, "user123");
        given(concertRepository.findByIdForUpdate(1L)).willReturn(Optional.of(concert));
        given(reservationRepository.save(any())).willReturn(saved);

        Reservation result = reservationService.reserve(request);

        assertThat(result.getConcertId()).isEqualTo(1L);
        assertThat(result.getUserId()).isEqualTo("user123");
        assertThat(concert.getRemainingTickets()).isEqualTo(9);
        verify(paymentClient).requestPayment(1L, "user123");
        verify(reservationRepository).save(any());
    }

    @Test
    void 콘서트가_존재하지_않으면_예외를_던진다() {
        given(concertRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.reserve(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(paymentClient, never()).requestPayment(any(), any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 잔여_티켓이_없으면_SoldOutException을_던진다() {
        Concert concert = Concert.builder().id(1L).name("콘서트 A").remainingTickets(0).build();
        given(concertRepository.findByIdForUpdate(1L)).willReturn(Optional.of(concert));

        assertThatThrownBy(() -> reservationService.reserve(request))
                .isInstanceOf(SoldOutException.class);

        verify(paymentClient, never()).requestPayment(any(), any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 결제가_실패하면_PaymentFailedException을_던지고_예매를_저장하지_않는다() {
        Concert concert = Concert.builder().id(1L).name("콘서트 A").remainingTickets(10).build();
        given(concertRepository.findByIdForUpdate(1L)).willReturn(Optional.of(concert));
        willThrow(new PaymentFailedException()).given(paymentClient).requestPayment(1L, "user123");

        assertThatThrownBy(() -> reservationService.reserve(request))
                .isInstanceOf(PaymentFailedException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void 결제_서버_불가시_PaymentUnavailableException을_던지고_예매를_저장하지_않는다() {
        Concert concert = Concert.builder().id(1L).name("콘서트 A").remainingTickets(10).build();
        given(concertRepository.findByIdForUpdate(1L)).willReturn(Optional.of(concert));
        willThrow(new PaymentUnavailableException()).given(paymentClient).requestPayment(1L, "user123");

        assertThatThrownBy(() -> reservationService.reserve(request))
                .isInstanceOf(PaymentUnavailableException.class);

        verify(reservationRepository, never()).save(any());
    }
}
