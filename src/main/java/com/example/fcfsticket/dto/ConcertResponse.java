package com.example.fcfsticket.dto;

import com.example.fcfsticket.domain.Concert;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConcertResponse {
    private Long id;
    private String name;
    private Integer remainingTickets;

    public static ConcertResponse from(Concert concert) {
        return ConcertResponse.builder()
                .id(concert.getId())
                .name(concert.getName())
                .remainingTickets(concert.getRemainingTickets())
                .build();
    }
}
