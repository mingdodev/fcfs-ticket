package com.example.fcfsticket.service;

import com.example.fcfsticket.domain.Concert;
import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.domain.ReservationStatus;
import com.example.fcfsticket.dto.ReservationRequest;
import com.example.fcfsticket.exception.ReservationStateException;
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
        return reservationRepository.save(Reservation.create(request.getConcertId(), request.getUserId()));
    }

    @Transactional
    public Reservation confirm(Long reservationId) {
        int updated = reservationRepository.updateStatusIfEquals(
                reservationId,
                ReservationStatus.PENDING,
                ReservationStatus.CONFIRMED
        );

        if (updated == 0) {
            throw new ReservationStateException("예약이 이미 만료되었거나 처리되어 확정할 수 없습니다.");
        }

        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));
    }

    @Transactional
    public boolean cancel(Long reservationId, Long concertId) {
        int updated = reservationRepository.updateStatusIfEquals(
                reservationId,
                ReservationStatus.PENDING,
                ReservationStatus.CANCELED
        );

        if (updated > 0) {
            concertRepository.increaseTicket(concertId);
            return true;
        }

        return false;
    }
}
