import http from 'k6/http';
import { Counter, Rate, Trend } from 'k6/metrics';

const bookingSuccess  = new Counter('booking_success');   // 201
const soldOut         = new Counter('booking_sold_out');  // 409
const paymentFailed   = new Counter('booking_payment_failed'); // 402
const paymentUnavail  = new Counter('booking_payment_unavail'); // 503
const systemError     = new Counter('booking_system_error');   // 500 + timeout + 네트워크
const noResponse      = new Counter('booking_no_response');    // timeout + 네트워크 실패
const soldOutLatency = new Trend('sold_out_latency');

http.setResponseCallback(http.expectedStatuses(201, 409, 402, 503));

export const options = {
    scenarios: {
        spike: {
            executor: 'shared-iterations',
            vus: 5000,
            iterations: 5000,
            maxDuration: '3m',
        },
    },
    thresholds: {
        booking_success:      ['count<=1000'],  // Oversell 방지
        booking_system_error: ['count<=50'],    // 시스템 오류율 ≤ 1% (5000건 기준)
        http_req_duration: [
            'p(95)<999999',
            'p(99)<999999',
        ]
    },
};

const BASE_URL = 'http://10.0.2.10:8080';

export default function () {
    let res;

    try {
        res = http.post(
            `${BASE_URL}/reservations`,
            JSON.stringify({
                concertId: 1,
                userId: `user-${__VU}`,
            }),
            {
                headers: { 'Content-Type': 'application/json' },
                timeout: '150s',
            }
        );
    } catch (e) {
        noResponse.add(1);
        systemError.add(1);
        console.log(`NETWORK FAIL VU=${__VU}: ${e}`);
        return;
    }

    if (res.status === 201) {
        bookingSuccess.add(1);

    } else if (res.status === 409) {
        soldOut.add(1);
        soldOutLatency.add(res.timings.duration);
    } else if (res.status === 402) {
        paymentFailed.add(1);

    } else if (res.status === 503) {
        paymentUnavail.add(1);

    } else {
        // 500 또는 예상치 못한 상태 코드
        systemError.add(1);
        console.log(
            `SYSTEM ERR [${res.status}] VU=${__VU}: ${res.body?.slice(0, 100)}`
        );
    }
}

export function handleSummary(data) {
    const m = data.metrics;
    const soldLatency = m.sold_out_latency?.values ?? {};

    const total       = m.iterations?.values.count ?? 0;
    const success     = m.booking_success?.values.count ?? 0;
    const sold        = m.booking_sold_out?.values.count ?? 0;
    const payFail     = m.booking_payment_failed?.values.count ?? 0;
    const payUnavail  = m.booking_payment_unavail?.values.count ?? 0;
    const noResp      = m.booking_no_response?.values.count ?? 0;
    const sysErr      = m.booking_system_error?.values.count ?? 0;

    // TPS 계산
    const durationMs  = m.iteration_duration?.values.max ?? 0;
    const durationS   = durationMs / 1000;
    const successTPS  =
        durationS > 0
            ? (success / durationS).toFixed(2)
            : 'N/A';

    // latency 값 가져오기
    const latency = m.http_req_duration?.values ?? {};

    const p50 = latency.med?.toFixed(2) ?? 'N/A';
    const p95 = latency['p(95)']?.toFixed(2) ?? 'N/A';
    const maxLatency = latency.max?.toFixed(2) ?? 'N/A';

    const sysErrRate =
        total > 0
            ? ((sysErr / total) * 100).toFixed(2)
            : '0.00';

    const recvRate =
        total > 0
            ? (((total - noResp) / total) * 100).toFixed(2)
            : '0.00';

    console.log('========== 실험 결과 요약 ==========');

    console.log(`총 요청수:          ${total}`);

    console.log('--- 응답 분류 ---');
    console.log(`  예매 성공  (201): ${success}`);
    console.log(`  매진       (409): ${sold}`);
    console.log(`  결제 실패  (402): ${payFail}`);
    console.log(`  결제 불가  (503): ${payUnavail}`);
    console.log(`  시스템 오류(500): ${sysErr - noResp}`);
    console.log(`  무응답(timeout):  ${noResp}`);

    console.log('--- 지표 ---');
    console.log(`  성공 TPS:         ${successTPS} req/s  (목표: ≥ 100)`);
    console.log(`  시스템 오류율:    ${sysErrRate}%        (목표: ≤ 1%)`);
    console.log(`  응답 수신율:      ${recvRate}%          (목표: ≥ 80%)`);

    console.log('--- Latency ---');
    console.log(`  p50 latency:      ${p50} ms`);
    console.log(`  p95 latency:      ${p95} ms`);
    console.log(`  max latency:      ${maxLatency} ms`);

    console.log('--- Sold Out Latency ---');
    console.log(`  409 p50 latency:  ${soldLatency.med?.toFixed(2) ?? 'N/A'} ms`);
    console.log(`  409 p95 latency:  ${soldLatency['p(95)']?.toFixed(2) ?? 'N/A'} ms`);
    console.log(`  409 p99 latency:  ${soldLatency['p(99)']?.toFixed(2) ?? 'N/A'} ms`);
    console.log(`  409 max latency:  ${soldLatency.max?.toFixed(2) ?? 'N/A'} ms`);
    console.log(`  Oversell 여부:    DB에서 직접 확인 필요`);
    console.log('====================================');

    return {
        'results/summary.json': JSON.stringify(data, null, 2),
    };
}
