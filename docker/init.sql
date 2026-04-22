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
  FOREIGN KEY (concert_id) REFERENCES concerts(id)
);

INSERT INTO concerts (id, name, remaining_tickets) VALUES
(1, '매숑이 콘서트', 1000),
(2, '매슝이 콘서트', 500),
(3, '메숭이 콘서트', 300);
