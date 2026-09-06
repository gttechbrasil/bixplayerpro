"""Load test: N devices call GET /device/config spread over a window, report latencies.

    uv run python scripts/loadtest_device_config.py --base http://localhost:8000 \
        --tokens loadtest-tokens.txt --duration 60 --concurrency 50

Each token is used once (one boot per device); requests are paced so that all of them are
issued within `--duration` seconds, which is the "2,000 devices booting in a minute" scenario
from the M5 plan. Prints p50/p95/p99, max, error count and throughput. Exit code 1 when p95
exceeds `--p95-ms` (default 300).
"""

import argparse
import asyncio
import statistics
import time
from pathlib import Path

import httpx


def percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    idx = min(len(ordered) - 1, int(round((pct / 100) * (len(ordered) - 1))))
    return ordered[idx]


async def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://localhost:8000")
    parser.add_argument("--tokens", type=Path, default=Path("loadtest-tokens.txt"))
    parser.add_argument("--duration", type=float, default=60.0)
    parser.add_argument("--concurrency", type=int, default=50)
    parser.add_argument("--p95-ms", type=float, default=300.0)
    args = parser.parse_args()

    tokens = [t for t in args.tokens.read_text(encoding="utf-8").split() if t]
    if not tokens:
        raise SystemExit("no tokens")
    interval = args.duration / len(tokens)
    sem = asyncio.Semaphore(args.concurrency)
    latencies: list[float] = []
    statuses: dict[int, int] = {}
    errors: list[str] = []

    async with httpx.AsyncClient(base_url=args.base, timeout=30.0) as client:

        async def one(token: str) -> None:
            async with sem:
                started = time.perf_counter()
                try:
                    resp = await client.get(
                        "/api/v1/device/config", headers={"Authorization": f"Bearer {token}"}
                    )
                    statuses[resp.status_code] = statuses.get(resp.status_code, 0) + 1
                    if resp.status_code != 200:
                        errors.append(f"{resp.status_code} {resp.text[:80]}")
                except Exception as exc:  # noqa: BLE001 - counted as a failure
                    errors.append(repr(exc)[:120])
                    return
                latencies.append((time.perf_counter() - started) * 1000)

        wall = time.perf_counter()
        tasks = []
        for n, token in enumerate(tokens):
            target = wall + n * interval
            delay = target - time.perf_counter()
            if delay > 0:
                await asyncio.sleep(delay)
            tasks.append(asyncio.create_task(one(token)))
        await asyncio.gather(*tasks)
        elapsed = time.perf_counter() - wall

    ok = statuses.get(200, 0)
    p50, p95, p99 = (percentile(latencies, p) for p in (50, 95, 99))
    print(f"requests: {len(tokens)}  ok: {ok}  errors: {len(errors)}  statuses: {statuses}")
    print(f"elapsed: {elapsed:.1f}s  throughput: {len(tokens) / elapsed:.1f} req/s")
    if latencies:
        print(
            f"latency ms  p50={p50:.0f}  p95={p95:.0f}  p99={p99:.0f}  "
            f"max={max(latencies):.0f}  mean={statistics.fmean(latencies):.0f}"
        )
    for line in errors[:5]:
        print("  error:", line)
    return 0 if p95 <= args.p95_ms and not errors else 1


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
