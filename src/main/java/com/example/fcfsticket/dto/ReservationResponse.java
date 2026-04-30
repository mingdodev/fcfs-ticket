package com.example.fcfsticket.dto;

import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.domain.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationResponse {
    private Long reservationId;
    private Long concertId;
    private String userId;
    private LocalDateTime createdAt;
    private ReservationStatus status;
    private String failureReason;  // CANCELED인 경우 실패 원인

    public static ReservationResponse from(Reservation reservation) {
        return ReservationResponse.builder()
                .reservationId(reservation.getId())
                .concertId(reservation.getConcertId())
                .userId(reservation.getUserId())
                .createdAt(reservation.getCreatedAt())
                .status(reservation.getStatus())
                .failureReason(null)  // 기본값
                .build();
    }

    public static ReservationResponse fromCanceled(Reservation reservation, String failureReason) {
        return ReservationResponse.builder()
                .reservationId(reservation.getId())
                .concertId(reservation.getConcertId())
                .userId(reservation.getUserId())
                .createdAt(reservation.getCreatedAt())
                .status(reservation.getStatus())
                .failureReason(failureReason)
                .build();
    }
}
