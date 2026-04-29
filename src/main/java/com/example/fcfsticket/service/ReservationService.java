package com.example.fcfsticket.service;

import com.example.fcfsticket.client.PaymentClient;
import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.dto.ReservationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationTxService reservationTxService;
    private final PaymentClient paymentClient;

    public Reservation reserve(ReservationRequest request) {
        log.info("reserve start: concertId={}, userId={}", request.getConcertId(), request.getUserId());

        Reservation reservation = reservationTxService.createPending(request);

        try {
            paymentClient.requestPayment(reservation.getConcertId(), reservation.getUserId());
        } catch (Exception e) {
            try {
                reservationTxService.cancel(reservation.getId(), reservation.getConcertId());
            } catch (Exception compensationEx) {
                log.error("compensation failed: reservationId={}", reservation.getId(), compensationEx);
            }
            throw e;
        }

        reservationTxService.confirm(reservation.getId());
        log.info("reserve success: reservationId={}, concertId={}, userId={}", reservation.getId(), request.getConcertId(), request.getUserId());
        return reservation;
    }
}
