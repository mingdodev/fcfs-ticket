package com.example.fcfsticket.repository;

import com.example.fcfsticket.domain.Concert;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConcertRepository extends JpaRepository<Concert, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Concert c where c.id = :id")
    Optional<Concert> findByIdForUpdate(@Param("id") long concertId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Concert c SET c.remainingTickets = c.remainingTickets - 1 WHERE c.id = :concertId AND c.remainingTickets > 0")
    int decreaseIfAvailable(@Param("concertId") Long concertId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Concert c SET c.remainingTickets = c.remainingTickets + 1 WHERE c.id = :concertId")
    int increaseTicket(@Param("concertId") Long concertId);
}
