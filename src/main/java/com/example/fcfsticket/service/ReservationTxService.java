package com.example.fcfsticket.service;

import com.example.fcfsticket.domain.Concert;
import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.dto.ReservationRequest;
import com.example.fcfsticket.repository.ConcertRepository;
import com.example.fcfsticket.repository.ReservationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationTxService {

    private final ReservationRepository reservationRepository;
    private final ConcertRepository concertRepository;

    @Transactional
    public Reservation createPending(ReservationRequest request) {
        Concert concert = concertRepository.findByIdForUpdate(request.getConcertId())
                .orElseThrow(() -> new IllegalArgumentException("해당 콘서트 정보를 찾을 수 없습니다."));
        concert.decreaseTicket();
        return reservationRepository.save(Reservation.create(request.getConcertId(), request.getUserId()));
    }

    @Transactional
    public void confirm(Long reservationId) {
        reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."))
                .confirm();
    }

    @Transactional
    public void cancel(Long reservationId, Long concertId) {
        reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."))
                .cancel();
        concertRepository.findById(concertId)
                .orElseThrow(() -> new IllegalArgumentException("콘서트를 찾을 수 없습니다."))
                .increaseTicket();
    }
}
