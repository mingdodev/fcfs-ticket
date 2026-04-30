CREATE TABLE IF NOT EXISTS concerts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  remaining_tickets INT NOT NULL
);

CREATE TABLE IF NOT EXISTS reservations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  concert_id BIGINT NOT NULL,
  user_id VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  expires_at DATETIME NOT NULL,
  FOREIGN KEY (concert_id) REFERENCES concerts(id)
);

CREATE TABLE IF NOT EXISTS reservation_compensation_states (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  reservation_id BIGINT NOT NULL,
  concert_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  retry_count INT NOT NULL DEFAULT 0,
  created_at BIGINT NOT NULL,
  failure_reason VARCHAR(50),
  UNIQUE KEY uk_reservation_id (reservation_id),
  FOREIGN KEY (concert_id) REFERENCES concerts(id)
);

INSERT INTO concerts (id, name, remaining_tickets) VALUES
(1, '매숑이 콘서트', 1000),
(2, '매슝이 콘서트', 500),
(3, '메숭이 콘서트', 300);
