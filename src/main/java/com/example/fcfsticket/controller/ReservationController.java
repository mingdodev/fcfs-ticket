package com.example.fcfsticket.controller;

import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.domain.ReservationStatus;
import com.example.fcfsticket.dto.ReservationRequest;
import com.example.fcfsticket.dto.ReservationResponse;
import com.example.fcfsticket.repository.ReservationCompensationStateRepository;
import com.example.fcfsticket.repository.ReservationRepository;
import com.example.fcfsticket.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reservations")
@RequiredArgsConstructor
public class ReservationController {
    private final ReservationService reservationService;
    private final ReservationRepository reservationRepository;
    private final ReservationCompensationStateRepository compensationStateRepository;

    @PostMapping
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReservationRequest request) {
        Reservation reservation = reservationService.reserve(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ReservationResponse.from(reservation));
    }

    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationResponse> getReservation(@PathVariable Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("예약을 찾을 수 없습니다."));

        // CANCELED인 경우 실패 원인 포함
        if (reservation.getStatus() == ReservationStatus.CANCELED) {
            var compensationState = compensationStateRepository.findByReservationId(reservationId);
            if (compensationState.isPresent()) {
                return ResponseEntity.ok(
                    ReservationResponse.fromCanceled(
                        reservation,
                        compensationState.get().getFailureReason()
                    )
                );
            }
        }

        return ResponseEntity.ok(ReservationResponse.from(reservation));
    }
}
