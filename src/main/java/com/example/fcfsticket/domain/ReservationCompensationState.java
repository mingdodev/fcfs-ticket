package com.example.fcfsticket.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reservation_compensation_states")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationCompensationState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reservationId;

    @Column(nullable = false)
    private Long concertId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompensationStatus status;

    @Column(nullable = false)
    private int retryCount;

    @Column(nullable = false)
    private long createdAt;

    @Column
    private String failureReason;  // 실패 원인 (PAYMENT_FAILED, PAYMENT_UNAVAILABLE 등)

    public enum CompensationStatus {
        PENDING,      // Waiting to be compensated
        REDIS_SUCCESS, // Redis restored successfully
        DB_FAILED,    // DB update failed (needs retry)
        COMPLETED     // Fully compensated
    }

    public void markRedisSuccess() {
        this.status = CompensationStatus.REDIS_SUCCESS;
    }

    public void markDbFailed() {
        this.status = CompensationStatus.DB_FAILED;
        this.retryCount++;
    }

    public void markCompleted() {
        this.status = CompensationStatus.COMPLETED;
    }
}
