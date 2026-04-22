# FCFS Ticket

![Java](https://img.shields.io/badge/Java-21-007396?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0-6DB33F?style=flat&logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat&logo=mysql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?style=flat&logo=docker&logoColor=white)
![k6](https://img.shields.io/badge/k6-Load_Testing-7D64FF?style=flat&logo=k6&logoColor=white)

선착순(FCFS) 티켓 예매 서비스입니다. 오픈과 동시에 5,000명이 1,000장 한정 티켓에 동시 접근하는 상황을 가정합니다.
1,000장을 10초 안에 처리 가능한 구조를 목표로 하며, 1차 성능 목표는 성공 처리 TPS 100, 시스템 오류율 1% 이하입니다.

## 실험 목표

> 데이터 정합성을 유지한 상태에서, 소프트웨어 최적화와 아키텍처 및 인프라 확장이 처리량에 미치는 영향을 비교한다

- Oversell(초과 판매)은 어떤 상황에서도 허용하지 않습니다
- 동일한 트래픽 조건에서 버전별 처리량과 오류율을 비교합니다
- 각 버전은 서로 다른 동시성 제어 전략과 인프라 구성을 적용합니다
- v1에서는 단일 저사양 서버 환경에서 소프트웨어 최적화의 한계를 확인하고,
  v2에서는 아키텍처 개선을 통해 병목을 완화하며,
  v3에서는 인프라 확장을 통해 처리량의 확장 가능성을 검증합니다

## 핵심 제약

| 항목 | 값                          |
|---|----------------------------|
| 한정 티켓 수량 | 1,000장 |
| 동시 접속 사용자 | 5,000명 |
| 결제 API 지연 | 1~2초 (외부 I/O 병목 가정) |
| 1차 목표 TPS | 성공 처리 TPS 100 |
| 확장 목표 TPS | 성공 처리 TPS 200 |
| 서버 환경 | NCP 최저 사양 단일 인스턴스 + Docker |

## 버전별 전략

| 버전 | 레이어 | 핵심 전략 |
|---|---|---|
| v0 | — | 동시성 제어 없는 naive 구현 (baseline / 실패 케이스) |
| v1 | 소프트웨어 | DB Lock 전략, 트랜잭션 범위 조정, 커넥션·스레드 풀 튜닝 |
| v2 | 아키텍처 | Redis 선점 처리, Queue 기반 비동기 처리 구조 도입 |
| v3 | 인프라 | 다중 인스턴스 + 로드밸런싱, 수평 확장 효율 분석 |

→ [버전별 상세 전략](06-optimization-strategy.md)

## 서버 인프라 구성

브랜치별로 동시성 제어 전략과 인프라 구성이 다르며,
동일한 초기 데이터 기준으로 성능 차이를 반복 측정합니다.

```bash
./deploy.sh v0
```
버전 입력을 통해 현재 docker-compose.yml을 기반으로 도커 파일을 빌드하고 서버에 애플리케이션을 배포할 수 있습니다.

## 문서

- [비즈니스 상황](01-scenario.md)
- [성능 요구사항](02-performance-requirements.md)
- [테스트 환경 명세](03-test-environment.md)
- [실험 단계 계획 및 기술 리스크](04-implementation-plan.md)
- [API 명세서](05-api-specification.md)
- [버전별 최적화 전략](06-optimization-strategy.md)
