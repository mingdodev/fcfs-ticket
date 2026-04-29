package com.example.fcfsticket.repository;

import com.example.fcfsticket.domain.Reservation;
import com.example.fcfsticket.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByStatusAndExpiresAtBefore(ReservationStatus status, LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Reservation r SET r.status = :toStatus WHERE r.id = :id AND r.status = :fromStatus")
    int updateStatusIfEquals(@Param("id") Long id,
                             @Param("fromStatus") ReservationStatus fromStatus,
                             @Param("toStatus") ReservationStatus toStatus);
}
