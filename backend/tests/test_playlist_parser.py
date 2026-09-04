import pytest

from app.core.exceptions import ApiError
from app.models import Playlist
from app.services.playlists import (
    apply_parsed,
    parse_playlist_url,
    playlist_url_for_app,
    replace_host,
)

XTREAM = (
    "http://cnplay.click:8080/get.php?username=338679532&password=s3cr3t&type=m3u_plus&output=hls"
)


def test_parse_xtream_url() -> None:
    parsed = parse_playlist_url(XTREAM)
    assert parsed.type == "xtream"
    assert parsed.host == "http://cnplay.click:8080"
    assert parsed.username == "338679532"
    assert parsed.password == "s3cr3t"
    assert "password" not in parsed.url
    assert (
        parsed.url == "http://cnplay.click:8080/get.php?username=338679532&type=m3u_plus&output=hls"
    )


def test_parse_m3u_url() -> None:
    parsed = parse_playlist_url("https://exemplo.com/lista.m3u8")
    assert parsed.type == "m3u"
    assert parsed.host == "https://exemplo.com"
    assert parsed.username is None and parsed.password is None
    assert parsed.url == "https://exemplo.com/lista.m3u8"


@pytest.mark.parametrize("url", ["ftp://x/y", "cnplay.click/get.php", "", "http:///nohost"])
def test_parse_invalid_url(url: str) -> None:
    with pytest.raises(ApiError) as exc:
        parse_playlist_url(url)
    assert exc.value.status_code == 400


def test_apply_and_rebuild_url() -> None:
    playlist = Playlist(device_id=1, name="x")
    apply_parsed(playlist, parse_playlist_url(XTREAM))
    assert playlist.password_enc and "s3cr3t" not in playlist.password_enc
    assert "s3cr3t" not in playlist.url
    rebuilt = playlist_url_for_app(playlist)
    assert "password=s3cr3t" in rebuilt
    assert rebuilt.startswith("http://cnplay.click:8080/get.php?username=338679532")

    m3u = Playlist(device_id=1, name="y")
    apply_parsed(m3u, parse_playlist_url("http://a.b/c.m3u"))
    assert playlist_url_for_app(m3u) == "http://a.b/c.m3u"


def test_replace_host() -> None:
    assert (
        replace_host("http://old.com:80/get.php?username=u", "https://new.net")
        == "https://new.net/get.php?username=u"
    )
