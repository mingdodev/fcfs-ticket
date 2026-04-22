package com.example.fcfsticket.domain;

import com.example.fcfsticket.exception.SoldOutException;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "concerts")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Concert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer remainingTickets;

    public void decreaseTicket() {
        if (this.remainingTickets <= 0) throw new SoldOutException("잔여 티켓이 없습니다.");
        this.remainingTickets--;
    }
}
