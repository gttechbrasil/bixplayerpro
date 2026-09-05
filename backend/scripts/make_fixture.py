"""Generate the local test playlist used to exercise the Android app against a big list.

The output is an M3U with `--count` channels spread over eight categories, plus two malformed
entries the parser must skip. Channels 1-3 point at a public HLS test stream, channels 4-6 at
a local MPEG-TS sample served by the API's static `/uploads` route, and the rest at URLs that
return 404 (useful to exercise the player's retry/error path).

    uv run python scripts/make_fixture.py                  # writes uploads/fixture.m3u
    uv run python scripts/make_fixture.py --download-sample # also fetches uploads/fixture/sample.ts

`10.0.2.2` is the host machine as seen from the Android emulator; pass `--host` for a real
device on the LAN (e.g. `--host http://192.168.0.10:8000`).
"""

from __future__ import annotations

import argparse
import urllib.request
from pathlib import Path

CATEGORIES = [
    "Esportes",
    "Filmes",
    "Notícias",
    "Infantil",
    "Documentários",
    "Música",
    "Variedades",
    "Abertos",
]

# Mux's public HLS test stream (Big Buck Bunny, multi-bitrate) and four of its TS segments.
PUBLIC_HLS = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
PUBLIC_TS_SEGMENTS = [
    f"https://test-streams.mux.dev/x36xhzz/url_0/url_462/193039199_mp4_h264_aac_hd_{i}.ts"
    for i in range(7, 11)
]


def build_playlist(host: str, count: int) -> str:
    lines = ["#EXTM3U"]
    for n in range(1, count + 1):
        category = CATEGORIES[(n - 1) % len(CATEGORIES)]
        if n <= 3:
            url = PUBLIC_HLS
        elif n <= 6:
            url = f"{host}/uploads/fixture/sample.ts"
        else:
            url = f"{host}/fake/stream/{n}.ts"
        lines.append(
            f'#EXTINF:-1 tvg-id="canal{n}.br" tvg-name="Canal {n}" '
            f'tvg-logo="{host}/uploads/logo.png" tvg-chno="{n}" group-title="{category}",Canal {n} HD'
        )
        lines.append(url)
    # Two broken entries providers routinely ship; the parser must skip them, not the file.
    lines.append("#EXTINF sem virgula")
    lines.append(f"{host}/fake/orfao.ts")
    lines.append("#EXTINF:-1,Sem URL no fim")
    return "\n".join(lines) + "\n"


def download_sample(target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    with target.open("wb") as out:
        for url in PUBLIC_TS_SEGMENTS:
            with urllib.request.urlopen(url, timeout=60) as response:  # noqa: S310 - fixed https URLs
                out.write(response.read())


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="http://10.0.2.2:8000", help="base URL of the local API as the device sees it")
    parser.add_argument("--count", type=int, default=1200, help="number of channels")
    parser.add_argument("--out", type=Path, default=Path("uploads/fixture.m3u"))
    parser.add_argument("--download-sample", action="store_true", help="fetch uploads/fixture/sample.ts (~2 MB)")
    args = parser.parse_args()

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(build_playlist(args.host.rstrip("/"), args.count), encoding="utf-8")
    print(f"wrote {args.out} ({args.count} channels, {len(CATEGORIES)} categories, 2 malformed entries)")

    if args.download_sample:
        sample = args.out.parent / "fixture" / "sample.ts"
        download_sample(sample)
        print(f"wrote {sample} ({sample.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
