package com.example.fcfsticket.service;

import com.example.fcfsticket.client.PaymentClient;
import com.example.fcfsticket.domain.Concert;
import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.dto.ReservationRequest;
import com.example.fcfsticket.repository.ConcertRepository;
import com.example.fcfsticket.repository.ReservationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final ConcertRepository concertRepository;
    private final PaymentClient paymentClient;

    @Transactional
    public Reservation reserve(ReservationRequest request) {
        final long concertId = request.getConcertId();
        final String userId = request.getUserId();

        Concert concert = concertRepository.findByIdForUpdate(concertId)
                .orElseThrow(() -> new IllegalArgumentException("해당 콘서트 정보를 찾을 수 없습니다."));

        concert.decreaseTicket();

        paymentClient.requestPayment(concertId, userId);

        return reservationRepository.save(Reservation.create(concertId, userId));
    }
}
