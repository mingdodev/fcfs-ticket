package com.example.fcfsticket.service;

import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.dto.ReservationRequest;
import com.example.fcfsticket.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ReservationService {
    private final ReservationRepository reservationRepository;
    private final ConcertService concertService;

    public Reservation reserve(ReservationRequest request) {
        throw new UnsupportedOperationException("구현이 필요합니다.");
    }
}
