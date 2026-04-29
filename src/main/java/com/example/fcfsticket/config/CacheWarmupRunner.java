package com.example.fcfsticket.config;

import com.example.fcfsticket.repository.ConcertRepository;
import com.example.fcfsticket.service.TicketInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmupRunner implements ApplicationRunner {

    private final ConcertRepository concertRepository;
    private final TicketInventoryService ticketInventoryService;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Starting cache warmup...");

        concertRepository.findAll().forEach(concert -> {
            ticketInventoryService.initializeInventory(
                concert.getId(),
                concert.getRemainingTickets()
            );
        });

        log.info("Cache warmup completed");
    }
}
