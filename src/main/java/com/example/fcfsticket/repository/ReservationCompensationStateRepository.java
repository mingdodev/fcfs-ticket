package com.example.fcfsticket.repository;

import com.example.fcfsticket.domain.ReservationCompensationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationCompensationStateRepository extends JpaRepository<ReservationCompensationState, Long> {
    Optional<ReservationCompensationState> findByReservationId(Long reservationId);

    @Query(value = "SELECT * FROM reservation_compensation_states WHERE status IN ('PENDING', 'DB_FAILED') AND retry_count < 3 AND (UNIX_TIMESTAMP() * 1000 - created_at) > :expireMs ORDER BY created_at ASC", nativeQuery = true)
    List<ReservationCompensationState> findRetryableCompensations(@Param("expireMs") long expireMs);
}
