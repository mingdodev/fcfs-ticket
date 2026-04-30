import http from 'k6/http';
import { Counter, Rate, Trend } from 'k6/metrics';
import { sleep } from 'k6';

// ========== 접수 단계 메트릭 ==========
const acceptSuccess      = new Counter('accept_success');       // 202
const acceptSoldOut      = new Counter('accept_sold_out');      // 409
const acceptSystemErr    = new Counter('accept_system_err');    // 500
const acceptTimeout      = new Counter('accept_timeout');       // timeout
const acceptLatency      = new Trend('accept_latency');
const acceptSoldOutLatency = new Trend('accept_sold_out_latency');

// ========== 조회 단계 메트릭 ==========
const querySuccess       = new Counter('query_success');        // 200
const queryNotFound      = new Counter('query_not_found');      // 404
const querySystemErr     = new Counter('query_system_err');     // 500
const queryTimeout       = new Counter('query_timeout');        // timeout
const queryLatency       = new Trend('query_latency');
const querySuccessLatency = new Trend('query_success_latency');

// ========== 최종 상태 분류 ==========
const finalConfirmed     = new Counter('final_confirmed');      // CONFIRMED
const finalCanceled      = new Counter('final_canceled');       // CANCELED (전체)
const finalPaymentFailed = new Counter('final_payment_failed'); // CANCELED - 결제 실패
const finalPaymentUnavail = new Counter('final_payment_unavail'); // CANCELED - 결제 불가
const finalPending       = new Counter('final_pending');        // PENDING
const confirmedLatency   = new Trend('confirmed_latency');
const canceledLatency    = new Trend('canceled_latency');
const pendingLatency     = new Trend('pending_latency');

// ========== 전체 메트릭 ==========
const totalRequests      = new Counter('total_requests');       // 총 요청
const networkError       = new Counter('network_error');        // 네트워크 실패

http.setResponseCallback(http.expectedStatuses(202, 409, 200, 404, 500));

export const options = {
    scenarios: {
        spike: {
            executor: 'shared-iterations',
            vus: 5000,
            iterations: 5000,
            maxDuration: '5m',
        },
    },
    thresholds: {
        accept_success:      ['count<=1000'],   // Oversell 방지
        accept_system_err:   ['count<=50'],     // 시스템 오류율 ≤ 1%
        http_req_duration:   ['p(95)<999999'],
    },
};

const BASE_URL = 'http://10.0.2.10:8080';

export default function () {
    let acceptRes;
    let reservationId;

    totalRequests.add(1);

    // ========== 1단계: 예약 접수 (202 Accepted) ==========
    try {
        acceptRes = http.post(
            `${BASE_URL}/reservations`,
            JSON.stringify({
                concertId: 1,
                userId: `user-${__VU}-${Date.now()}`,
            }),
            {
                headers: { 'Content-Type': 'application/json' },
                timeout: '150s',
            }
        );

        acceptLatency.add(acceptRes.timings.duration);
    } catch (e) {
        networkError.add(1);
        acceptTimeout.add(1);
        acceptSystemErr.add(1);
        console.log(`[접수] TIMEOUT/NETWORK VU=${__VU}: ${e}`);
        return;
    }

    if (acceptRes.status === 202) {
        acceptSuccess.add(1);

        // reservationId 추출
        try {
            const body = JSON.parse(acceptRes.body);
            reservationId = body.reservationId;
        } catch (e) {
            console.log(`[접수] JSON Parse Error: ${e}`);
            acceptSystemErr.add(1);
            return;
        }
    } else if (acceptRes.status === 409) {
        acceptSoldOut.add(1);
        acceptSoldOutLatency.add(acceptRes.timings.duration);
        return; // 매진이면 조회 안 함
    } else if (acceptRes.status === 500) {
        acceptSystemErr.add(1);
        console.log(`[접수] 500 ERROR VU=${__VU}`);
        return;
    } else {
        acceptSystemErr.add(1);
        console.log(`[접수] UNEXPECTED [${acceptRes.status}] VU=${__VU}`);
        return;
    }

    // ========== 2단계: 최종 상태 조회 (GET) ==========
    // 결제 처리 완료 대기: 폴링으로 PENDING이 아닐 때까지 확인
    // 최대 10초 대기 (1 + 2 + 4초)
    let queryRes;
    let finalStatus;
    const maxRetries = 3;
    let attempts = 0;
    let waitTime = 1; // 1초 시작

    // PENDING이 아닐 때까지 폴링
    while (attempts < maxRetries && (!finalStatus || finalStatus === 'PENDING')) {
        sleep(waitTime);

        try {
            queryRes = http.get(
                `${BASE_URL}/reservations/${reservationId}`,
                {
                    headers: { 'Content-Type': 'application/json' },
                    timeout: '150s',
                }
            );

            queryLatency.add(queryRes.timings.duration);
        } catch (e) {
            networkError.add(1);
            queryTimeout.add(1);
            querySystemErr.add(1);
            console.log(`[조회] TIMEOUT/NETWORK VU=${__VU}: ${e}`);
            return;
        }

        if (queryRes.status === 200) {
            try {
                const body = JSON.parse(queryRes.body);
                finalStatus = body.status;

                // PENDING이 아니면 루프 탈출
                if (finalStatus !== 'PENDING') {
                    break;
                }
            } catch (e) {
                console.log(`[조회] JSON Parse Error: ${e}`);
                break;
            }
        } else {
            break;
        }

        attempts++;
        waitTime *= 2;  // exponential backoff: 1 → 2 → 4
    }

    // 최종 결과 처리
    if (queryRes.status === 200) {
        querySuccess.add(1);
        querySuccessLatency.add(queryRes.timings.duration);

        try {
            const body = JSON.parse(queryRes.body);
            const status = body.status;
            const failureReason = body.failureReason;

            if (status === 'CONFIRMED') {
                finalConfirmed.add(1);
                confirmedLatency.add(queryRes.timings.duration);
            } else if (status === 'CANCELED') {
                finalCanceled.add(1);
                canceledLatency.add(queryRes.timings.duration);

                // 결제 실패 원인 분류
                if (failureReason === 'PAYMENT_FAILED') {
                    finalPaymentFailed.add(1);
                } else if (failureReason === 'PAYMENT_UNAVAILABLE') {
                    finalPaymentUnavail.add(1);
                }
            } else if (status === 'PENDING') {
                finalPending.add(1);
                pendingLatency.add(queryRes.timings.duration);
            }
        } catch (e) {
            console.log(`[조회] JSON Parse Error: ${e}`);
        }
    } else if (queryRes.status === 404) {
        queryNotFound.add(1);
        console.log(`[조회] 404 Not Found reservationId=${reservationId}`);
    } else if (queryRes.status === 500) {
        querySystemErr.add(1);
        console.log(`[조회] 500 ERROR reservationId=${reservationId}`);
    } else {
        querySystemErr.add(1);
        console.log(`[조회] UNEXPECTED [${queryRes.status}] reservationId=${reservationId}`);
    }
}

export function handleSummary(data) {
    const m = data.metrics;

    // 접수 단계
    const acceptSucc   = m.accept_success?.values.count ?? 0;
    const acceptSoldout = m.accept_sold_out?.values.count ?? 0;
    const acceptSysErr = m.accept_system_err?.values.count ?? 0;
    const acceptTO     = m.accept_timeout?.values.count ?? 0;
    const acceptLat    = m.accept_latency?.values ?? {};
    const acceptSOLat  = m.accept_sold_out_latency?.values ?? {};

    // 조회 단계
    const querySucc    = m.query_success?.values.count ?? 0;
    const queryNotFound = m.query_not_found?.values.count ?? 0;
    const querySysErr  = m.query_system_err?.values.count ?? 0;
    const queryTO      = m.query_timeout?.values.count ?? 0;
    const queryLat     = m.query_latency?.values ?? {};
    const querySuccLat = m.query_success_latency?.values ?? {};

    // 최종 상태
    const confirmed    = m.final_confirmed?.values.count ?? 0;
    const canceled     = m.final_canceled?.values.count ?? 0;
    const paymentFailed = m.final_payment_failed?.values.count ?? 0;
    const paymentUnavail = m.final_payment_unavail?.values.count ?? 0;
    const pending      = m.final_pending?.values.count ?? 0;
    const confirmedLat = m.confirmed_latency?.values ?? {};
    const canceledLat  = m.canceled_latency?.values ?? {};
    const pendingLat   = m.pending_latency?.values ?? {};

    // 전체
    const total        = m.total_requests?.values.count ?? 0;
    const netErr       = m.network_error?.values.count ?? 0;

    // TPS 계산
    const durationMs   = m.iteration_duration?.values.max ?? 0;
    const durationS    = durationMs / 1000;

    const acceptTPS = durationS > 0 ? (acceptSucc / durationS).toFixed(2) : 'N/A';
    const queryTPS  = durationS > 0 ? (querySucc / durationS).toFixed(2) : 'N/A';

    // 오류율
    const acceptErrRate = acceptSucc > 0 ? (((acceptSysErr + acceptTO) / acceptSucc) * 100).toFixed(2) : '0.00';
    const queryErrRate  = querySucc > 0 ? (((querySysErr + queryTO) / querySucc) * 100).toFixed(2) : '0.00';
    const netErrRate    = total > 0 ? ((netErr / total) * 100).toFixed(2) : '0.00';

    // HTTP latency 메트릭
    const httpLat = m.http_req_duration?.values ?? {};
    const p50 = httpLat.med?.toFixed(2) ?? 'N/A';
    const p95 = httpLat['p(95)']?.toFixed(2) ?? 'N/A';
    const p99 = httpLat['p(99)']?.toFixed(2) ?? 'N/A';
    const maxLat = httpLat.max?.toFixed(2) ?? 'N/A';

    console.log('\n\n');
    console.log('╔══════════════════════════════════════════════════════════════╗');
    console.log('║               예약 시스템 성능 테스트 결과                        ║');
    console.log('╚══════════════════════════════════════════════════════════════╝\n');

    // ========== 접수 단계 (POST /reservations) ==========
    console.log('┌─ [1단계] 예약 접수 (POST /reservations → 202 Accepted) ─┐');
    console.log('--- 응답 분류 ---');
    console.log(`  접수 성공      (202): ${acceptSucc}`);
    console.log(`  매진           (409): ${acceptSoldout}`);
    console.log(`  시스템 오류    (500): ${acceptSysErr - acceptTO}`);
    console.log(`  타임아웃(timeout):   ${acceptTO}`);
    console.log('--- 지표 ---');
    console.log(`  접수 TPS:            ${acceptTPS} req/s          (목표: ≥ 1000)`);
    console.log(`  시스템 오류율:       ${acceptErrRate}%                (목표: ≤ 1%)`);
    console.log('--- Latency (접수) ---');
    console.log(`  p50 latency:         ${acceptLat.med?.toFixed(2) ?? 'N/A'} ms`);
    console.log(`  p95 latency:         ${acceptLat['p(95)']?.toFixed(2) ?? 'N/A'} ms`);
    console.log(`  p99 latency:         ${acceptLat['p(99)']?.toFixed(2) ?? 'N/A'} ms`);
    console.log(`  max latency:         ${acceptLat.max?.toFixed(2) ?? 'N/A'} ms`);
    console.log('--- Latency (매진 409) ---');
    console.log(`  p50 latency:         ${acceptSOLat.med?.toFixed(2) ?? 'N/A'} ms`);
    console.log(`  p95 latency:         ${acceptSOLat['p(95)']?.toFixed(2) ?? 'N/A'} ms`);
    console.log(`  p99 latency:         ${acceptSOLat['p(99)']?.toFixed(2) ?? 'N/A'} ms`);
    console.log(`  max latency:         ${acceptSOLat.max?.toFixed(2) ?? 'N/A'} ms`);
    console.log('└─────────────────────────────────────────────────────────────┘\n');

    // ========== 조회 단계 (GET /reservations/{id}) ==========
    console.log('┌─ [2단계] 최종 상태 조회 (GET /reservations/{id} → 200) ─┐');
    console.log('--- 응답 분류 ---');
    console.log(`  조회 성공      (200): ${querySucc}`);
    console.log(`  찾을 수 없음    (404): ${queryNotFound}`);
    console.log(`  시스템 오류    (500): ${querySysErr - queryTO}`);
    console.log(`  타임아웃(timeout):   ${queryTO}`);
    console.log('--- 지표 ---');
    console.log(`  조회 TPS:            ${queryTPS} req/s          (목표: ≥ 5000)`);
    console.log(`  시스템 오류율:       ${queryErrRate}%                (목표: ≤ 1%)`);
    console.log('--- Latency (조회) ---');
    console.log(`  p50 latency:         ${queryLat.med?.toFixed(2) ?? 'N/A'} ms`);
    console.log(`  p95 latency:         ${queryLat['p(95)']?.toFixed(2) ?? 'N/A'} ms`);
    console.log(`  p99 latency:         ${queryLat['p(99)']?.toFixed(2) ?? 'N/A'} ms`);
    console.log(`  max latency:         ${queryLat.max?.toFixed(2) ?? 'N/A'} ms`);
    console.log('└─────────────────────────────────────────────────────────────┘\n');

    // ========== 최종 예약 상태 분류 ==========
    console.log('┌─ 최종 예약 상태 분류 ──────────────────────────────────────┐');
    console.log(`  CONFIRMED (예매 성공): ${confirmed}`);
    if (confirmed > 0) {
        console.log(`    └─ p50: ${confirmedLat.med?.toFixed(2) ?? 'N/A'} ms | p95: ${confirmedLat['p(95)']?.toFixed(2) ?? 'N/A'} ms | p99: ${confirmedLat['p(99)']?.toFixed(2) ?? 'N/A'} ms`);
    }
    console.log(`  CANCELED (예매 취소):   ${canceled}`);
    if (canceled > 0) {
        console.log(`    └─ 결제 실패 (402):     ${paymentFailed}`);
        console.log(`    └─ 결제 불가 (503):    ${paymentUnavail}`);
        console.log(`    └─ p50: ${canceledLat.med?.toFixed(2) ?? 'N/A'} ms | p95: ${canceledLat['p(95)']?.toFixed(2) ?? 'N/A'} ms | p99: ${canceledLat['p(99)']?.toFixed(2) ?? 'N/A'} ms`);
    }
    console.log(`  PENDING (처리 중):    ${pending}`);
    if (pending > 0) {
        console.log(`    └─ p50: ${pendingLat.med?.toFixed(2) ?? 'N/A'} ms | p95: ${pendingLat['p(95)']?.toFixed(2) ?? 'N/A'} ms | p99: ${pendingLat['p(99)']?.toFixed(2) ?? 'N/A'} ms`);
    }
    console.log('└─────────────────────────────────────────────────────────────┘\n');

    // ========== 전체 시스템 지표 ==========
    console.log('┌─ 전체 시스템 지표 ─────────────────────────────────────────┐');
    console.log(`  총 요청수:           ${total}`);
    console.log(`  네트워크 오류:       ${netErr}                  (오류율: ${netErrRate}%)`);
    console.log(`  응답 수신율:         ${((total - netErr) / total * 100).toFixed(2)}%                (목표: ≥ 80%)`);
    console.log('--- 전체 HTTP Latency ---');
    console.log(`  p50 latency:         ${p50} ms`);
    console.log(`  p95 latency:         ${p95} ms`);
    console.log(`  p99 latency:         ${p99} ms`);
    console.log(`  max latency:         ${maxLat} ms`);
    console.log('└─────────────────────────────────────────────────────────────┘\n');

    // ========== 최종 요약 ==========
    console.log('╔══════════════════════════════════════════════════════════════╗');
    console.log(`║ 접수 TPS: ${String(acceptTPS).padStart(6)} req/s │ 조회 TPS: ${String(queryTPS).padStart(6)} req/s │ 예매 성공: ${String(confirmed).padStart(6)} 건 │`);
    console.log('╚══════════════════════════════════════════════════════════════╝\n');

    return {
        'results/summary.json': JSON.stringify(data, null, 2),
    };
}
