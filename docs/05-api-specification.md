# API 명세서

## 개요

FCFS 티켓 예매 서비스의 공개 API 두 가지:
1. 공연 목록 조회
2. 티켓 예매 요청

---

## 1. 공연 목록 조회 API

### 요청

```
GET /concerts
```

**파라미터**: 없음

**응답 예시 (200 OK)**:

```json
{
  "concerts": [
    {
      "id": 1,
      "name": "2024 K-pop Festival",
      "remainingTickets": 1000
    }
  ]
}
```

### 응답 상세

| 필드 | 타입 | 설명 |
|---|---|---|
| `concerts` | Array | 공연 목록 |
| `concerts[].id` | Long | 공연 ID |
| `concerts[].name` | String | 공연명 |
| `concerts[].remainingTickets` | Integer | 남은 티켓 수 |

### 에러 응답

| 상태 코드 | 에러 | 설명 |
|---|---|---|
| 500 | `INTERNAL_SERVER_ERROR` | 서버 내부 오류 |

---

## 2. 티켓 예매 요청 API

### 요청

```
POST /reservations
Content-Type: application/json
```

**요청 바디**:

```json
{
  "concertId": 1,
  "userId": "user123"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `concertId` | Long | ✓ | 공연 ID |
| `userId` | String | ✓ | 사용자 ID (고유값) |

### 성공 응답 (201 Created)

```json
{
  "reservationId": 42,
  "concertId": 1,
  "userId": "user123",
  "createdAt": "2024-04-22T10:30:45.123Z"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `reservationId` | Long | 예매 ID (DB 레코드 ID) |
| `concertId` | Long | 공연 ID |
| `userId` | String | 사용자 ID |
| `createdAt` | ISO8601 | 예매 생성 시각 (UTC) |

### 에러 응답

| 상태 코드 | 에러 | 설명 |
|---|---|---|
| 400 | `INVALID_REQUEST` | 필드 누락 또는 타입 오류 |
| 409 | `SOLD_OUT` | 티켓 매진 (remainingTickets = 0) |
| 402 | `PAYMENT_FAILED` | 결제 실패 |
| 503 | `PAYMENT_UNAVAILABLE` | 결제 시스템 타임아웃 또는 미응답 |
| 500 | `INTERNAL_SERVER_ERROR` | 서버 내부 오류 |

**에러 응답 형식**:

```json
{
  "error": "SOLD_OUT",
  "message": "더 이상 예매 가능한 티켓이 없습니다."
}
```

---

## 3. 트랜잭션 보장 사항

### 예매 성공 (201 Created)

1. ✓ 티켓 수량이 1 감소 (`concert.remainingTickets -= 1`)
2. ✓ `Reservation` 레코드 저장 완료
3. ✓ 외부 결제 시스템에서 결제 승인 완료

### 예매 실패 (4xx / 5xx)

1. ✓ `Reservation` 레코드 미저장
2. ✓ 티켓 수량 미변경
3. ✓ 외부 결제 시스템 호출 없음 또는 결제 취소 처리

> **핵심**: DB 예매 레코드의 존재 = 예매 성공 + 결제 완료 상태를 보장합니다. 불완전한 상태의 레코드는 저장되지 않습니다.
