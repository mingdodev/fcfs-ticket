package com.example.fcfsticket.dto;

import com.example.fcfsticket.domain.Reservation;
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

    public static ReservationResponse from(Reservation reservation) {
        return ReservationResponse.builder()
                .reservationId(reservation.getId())
                .concertId(reservation.getConcertId())
                .userId(reservation.getUserId())
                .createdAt(reservation.getCreatedAt())
                .build();
    }
}
