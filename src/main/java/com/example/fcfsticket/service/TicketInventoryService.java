package com.example.fcfsticket.service;

import com.example.fcfsticket.repository.ConcertRepository;
import jakarta.transaction.Transactional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketInventoryService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ConcertRepository concertRepository;

    @Value("${ticket.inventory.ttl-seconds:259200}")
    private long ttlSeconds;

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

    /**
     * 선착순 선점: 원자적 감소
     * @return 성공 시 true (remaining >= 0), 실패 시 false (sold out)
     */
    public boolean reserveIfAvailable(Long concertId) {
        String key = getInventoryKey(concertId);
        Long remaining = redisTemplate.opsForValue().decrement(key);

        if (remaining == null) {
            // 첫 요청이고 Redis에 없는 경우 (캐시 워밍 실패)
            log.warn("Inventory not found in Redis: concertId={}", concertId);
            return false;
        }

        return remaining >= 0;
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

    /**
     * Redis → DB 동기화
     */
    @Transactional
    public void syncToDatabase(Long concertId) {
        Long currentInventory = getCurrentInventory(concertId);
        if (currentInventory == null) {
            log.warn("No inventory found in Redis for concertId={}", concertId);
            return;
        }

        concertRepository.findById(concertId).ifPresent(concert -> {
            concert.setRemainingTickets(currentInventory.intValue());
        });
        log.info("Synced to database: concertId={}, remaining={}", concertId, currentInventory);
    }
}
