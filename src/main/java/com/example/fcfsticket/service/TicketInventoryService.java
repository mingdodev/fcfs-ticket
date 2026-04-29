package com.example.fcfsticket.service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketInventoryService {

    private final RedisTemplate<String, String> redisTemplate;

    @Value("${ticket.inventory.ttl-seconds:259200}")
    private long ttlSeconds;

    private RedisScript<Long> reserveScript;

    @PostConstruct
    public void initScript() {
        this.reserveScript = RedisScript.of(
            new ClassPathResource("lua/reserve.lua"),
            Long.class
        );
    }

    private String getInventoryKey(Long concertId) {
        return "ticket:inventory:" + concertId;
    }

    /**
     * 캐시 워밍: DB의 remaining_tickets을 Redis에 로드
     */
    public void initializeInventory(Long concertId, int initialTickets) {
        String key = getInventoryKey(concertId);
        redisTemplate.opsForValue().set(key, String.valueOf(initialTickets));
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        log.info("Initialize inventory: concertId={}, tickets={}, ttl={}s", concertId, initialTickets, ttlSeconds);
    }

    public static class ReservationResult {
        public static final long SUCCESS = 1L;
        public static final long SOLD_OUT = 0L;
        public static final long DUPLICATE = -1L;
    }

    /**
     * 선착순 선점: Lua 스크립트로 원자적 처리
     * @return 1 (성공), 0 (매진), -1 (중복)
     */
    public long reserveIfAvailable(Long concertId, String userId) {
        String inventoryKey = getInventoryKey(concertId);
        String usersKey = "ticket:users:" + concertId;

        Long result = redisTemplate.execute(
            reserveScript,
            List.of(inventoryKey, usersKey),
            userId
        );

        if (result == null) {
            log.warn("Lua script returned null: concertId={}", concertId);
            return ReservationResult.SOLD_OUT;
        }

        if (result == ReservationResult.DUPLICATE) {
            log.info("Duplicate reservation attempt: concertId={}, userId={}", concertId, userId);
        } else if (result == ReservationResult.SOLD_OUT) {
            log.warn("Sold out: concertId={}", concertId);
        }

        return result;
    }

    /**
     * 복구: 결제 실패 시 티켓 복구
     */
    public void restoreTicket(Long concertId) {
        String key = getInventoryKey(concertId);
        redisTemplate.opsForValue().increment(key);
    }

    /**
     * 현재 재고 조회 (모니터링용)
     */
    public Long getCurrentInventory(Long concertId) {
        String key = getInventoryKey(concertId);
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? null : Long.parseLong(value);
    }

}
