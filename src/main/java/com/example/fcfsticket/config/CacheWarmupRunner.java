package com.example.fcfsticket.config;

import com.example.fcfsticket.repository.ConcertRepository;
import com.example.fcfsticket.service.TicketInventoryService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CacheWarmupRunner implements ApplicationRunner {

    private final ConcertRepository concertRepository;
    private final TicketInventoryService ticketInventoryService;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${ticket.inventory.ttl-seconds:259200}")
    private long ttlSeconds;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("Starting cache warmup...");

        concertRepository.findAll().forEach(concert -> {
            ticketInventoryService.initializeInventory(
                concert.getId(),
                concert.getRemainingTickets()
            );
            initializeUsersSet(concert.getId());
        });

        log.info("Cache warmup completed");
    }

    private void initializeUsersSet(Long concertId) {
        String usersKey = "ticket:users:" + concertId;
        redisTemplate.expire(usersKey, ttlSeconds, TimeUnit.SECONDS);
    }
}
