package com.example.fcfsticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationRequest {
    @NotNull(message = "concertId는 필수입니다.")
    private Long concertId;

    @NotBlank(message = "userId는 필수입니다.")
    private String userId;
}
