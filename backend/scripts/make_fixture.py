"""Generate the local test playlist used to exercise the Android app against a big list.

The output is an M3U with `--channels` live channels over eight categories, `--movies`
movies over ten genres, `--series` shows with three seasons of eight episodes each, and two
malformed entries the parser must skip. Channels 1-3 point at a public HLS test stream,
channels 4-6 at a local MPEG-TS sample served by the API's static `/uploads` route, the rest
at URLs that return 404 (to exercise the player's retry/error path). Movies and episodes point
at a local MP4 sample. `--epg` also writes an XMLTV guide covering -6 h..+48 h for the first
`--epg-channels` channels, and the playlist header advertises it through `url-tvg`.

    uv run python scripts/make_fixture.py                    # writes uploads/fixture.m3u (+ epg.xml)
    uv run python scripts/make_fixture.py --download-sample  # also fetches sample.ts / sample.mp4
    uv run python scripts/make_fixture.py --movies 20000 --series 500   # the M4 stress fixture

`10.0.2.2` is the host machine as seen from the Android emulator; pass `--host` for a real
device on the LAN (e.g. `--host http://192.168.0.10:8000`).
"""

from __future__ import annotations

import argparse
import random
import urllib.request
from datetime import datetime, timedelta, timezone
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
MOVIE_GENRES = ["Ação", "Comédia", "Drama", "Terror", "Ficção", "Animação", "Romance", "Suspense", "Aventura", "Nacional"]
SERIES_GENRES = ["Drama", "Comédia", "Crime", "Ficção", "Animes"]
PROGRAMMES = ["Jornal", "Novela", "Filme da Tarde", "Esporte Total", "Documentário", "Desenhos", "Show ao Vivo", "Debate"]

# Mux's public HLS test stream (Big Buck Bunny, multi-bitrate) and four of its TS segments.
PUBLIC_HLS = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
PUBLIC_TS_SEGMENTS = [
    f"https://test-streams.mux.dev/x36xhzz/url_0/url_462/193039199_mp4_h264_aac_hd_{i}.ts"
    for i in range(7, 11)
]
# Small public MP4 (~30 s, H.264/AAC) used for every movie and episode.
PUBLIC_MP4 = "https://filesamples.com/samples/video/mp4/sample_640x360.mp4"


def build_playlist(host: str, channels: int, movies: int, series: int, epg_channels: int, with_epg: bool) -> str:
    header = "#EXTM3U"
    if with_epg:
        header += f' url-tvg="{host}/uploads/epg.xml"'
    lines = [header]
    logo = f"{host}/uploads/logo.png"

    for n in range(1, channels + 1):
        category = CATEGORIES[(n - 1) % len(CATEGORIES)]
        if n <= 3:
            url = PUBLIC_HLS
        elif n <= 6:
            url = f"{host}/uploads/fixture/sample.ts"
        else:
            url = f"{host}/fake/stream/{n}.ts"
        tvg_id = f'tvg-id="canal{n}.br" ' if n <= epg_channels or not with_epg else ""
        lines.append(
            f'#EXTINF:-1 {tvg_id}tvg-name="Canal {n}" tvg-logo="{logo}" tvg-chno="{n}" '
            f'group-title="{category}",Canal {n} HD'
        )
        lines.append(url)

    rng = random.Random(42)
    for n in range(1, movies + 1):
        genre = MOVIE_GENRES[(n - 1) % len(MOVIE_GENRES)]
        year = 1980 + rng.randrange(46)
        lines.append(
            f'#EXTINF:-1 tvg-logo="{logo}" group-title="Filmes | {genre}",Filme {n} ({year})'
        )
        lines.append(f"{host}/uploads/fixture/sample.mp4?movie={n}")

    for n in range(1, series + 1):
        genre = SERIES_GENRES[(n - 1) % len(SERIES_GENRES)]
        for season in range(1, 4):
            for episode in range(1, 9):
                lines.append(
                    f'#EXTINF:-1 tvg-logo="{logo}" group-title="Séries | {genre}",'
                    f"Série {n} S{season:02d}E{episode:02d} - Episódio {episode}"
                )
                lines.append(f"{host}/uploads/fixture/sample.mp4?series={n}&s={season}&e={episode}")

    # Two broken entries providers routinely ship; the parser must skip them, not the file.
    lines.append("#EXTINF sem virgula")
    lines.append(f"{host}/fake/orfao.ts")
    lines.append("#EXTINF:-1,Sem URL no fim")
    return "\n".join(lines) + "\n"


def build_epg(epg_channels: int) -> str:
    """XMLTV with 30-minute programmes from -6 h to +48 h, aligned to the half hour."""
    now = datetime.now(timezone.utc).replace(minute=0, second=0, microsecond=0)
    start = now - timedelta(hours=6)
    end = now + timedelta(hours=48)
    fmt = "%Y%m%d%H%M%S +0000"
    out = ['<?xml version="1.0" encoding="UTF-8"?>', '<tv generator-info-name="bix-fixture">']
    for n in range(1, epg_channels + 1):
        out.append(f'  <channel id="canal{n}.br"><display-name>Canal {n} HD</display-name></channel>')
    rng = random.Random(7)
    for n in range(1, epg_channels + 1):
        t = start
        while t < end:
            length = rng.choice([30, 60, 90])
            stop = min(t + timedelta(minutes=length), end)
            title = rng.choice(PROGRAMMES)
            out.append(
                f'  <programme start="{t.strftime(fmt)}" stop="{stop.strftime(fmt)}" channel="canal{n}.br">'
                f"<title>{title}</title><desc>{title} no Canal {n}.</desc></programme>"
            )
            t = stop
    out.append("</tv>")
    return "\n".join(out) + "\n"


def _open(url: str, timeout: int):
    # Some public hosts refuse Python's default agent; a browser-like one is accepted everywhere.
    request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (bix-fixture)"})
    return urllib.request.urlopen(request, timeout=timeout)  # noqa: S310 - fixed https URLs


def download(url: str, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    with _open(url, 120) as response, target.open("wb") as out:
        out.write(response.read())


def download_samples(fixture_dir: Path) -> None:
    ts = fixture_dir / "sample.ts"
    fixture_dir.mkdir(parents=True, exist_ok=True)
    with ts.open("wb") as out:
        for url in PUBLIC_TS_SEGMENTS:
            with _open(url, 60) as response:
                out.write(response.read())
    download(PUBLIC_MP4, fixture_dir / "sample.mp4")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--host", default="http://10.0.2.2:8000", help="base URL of the local API as the device sees it")
    parser.add_argument("--channels", type=int, default=1200)
    parser.add_argument("--movies", type=int, default=2000)
    parser.add_argument("--series", type=int, default=50, help="shows; each has 3 seasons x 8 episodes")
    parser.add_argument("--epg-channels", type=int, default=200, help="channels that get a tvg-id and guide data")
    parser.add_argument("--no-epg", action="store_true")
    parser.add_argument("--out", type=Path, default=Path("uploads/fixture.m3u"))
    parser.add_argument("--download-sample", action="store_true", help="fetch uploads/fixture/sample.ts and sample.mp4")
    args = parser.parse_args()

    host = args.host.rstrip("/")
    with_epg = not args.no_epg
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(
        build_playlist(host, args.channels, args.movies, args.series, args.epg_channels, with_epg),
        encoding="utf-8",
    )
    print(
        f"wrote {args.out}: {args.channels} channels, {args.movies} movies, "
        f"{args.series} series ({args.series * 24} episodes), 2 malformed entries"
    )
    if with_epg:
        epg = args.out.parent / "epg.xml"
        epg.write_text(build_epg(args.epg_channels), encoding="utf-8")
        print(f"wrote {epg}: {args.epg_channels} channels, -6h..+48h")

    if args.download_sample:
        download_samples(args.out.parent / "fixture")
        for name in ("sample.ts", "sample.mp4"):
            f = args.out.parent / "fixture" / name
            print(f"wrote {f} ({f.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
