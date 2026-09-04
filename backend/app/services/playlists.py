"""Playlist URL parsing and persistence helpers.

Xtream Codes playlists look like:
    http://host:port/get.php?username=U&password=P&type=m3u_plus&output=ts
The password is never stored in clear: it is removed from `url`, encrypted into
`password_enc` and re-injected when the URL is delivered to the app.
"""

from dataclasses import dataclass
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from app.core.exceptions import bad_request
from app.core.security import decrypt_secret, encrypt_secret
from app.models import Playlist

MSG_INVALID_URL = "URL da playlist inválida. Informe um endereço http(s) completo."


@dataclass(frozen=True)
class ParsedPlaylist:
    type: str  # "xtream" | "m3u"
    host: str  # scheme://netloc
    url: str  # url without the password (xtream) or unchanged (m3u)
    username: str | None
    password: str | None


def parse_playlist_url(raw_url: str) -> ParsedPlaylist:
    url = raw_url.strip()
    parts = urlsplit(url)
    if parts.scheme not in ("http", "https") or not parts.netloc:
        raise bad_request(MSG_INVALID_URL, "invalid_playlist_url")

    host = f"{parts.scheme}://{parts.netloc}"
    query = parse_qsl(parts.query, keep_blank_values=True)
    params = {k.lower(): v for k, v in query}
    username = params.get("username")
    password = params.get("password")

    if username is not None and password is not None:
        cleaned = [(k, v) for k, v in query if k.lower() != "password"]
        stripped = urlunsplit(
            (parts.scheme, parts.netloc, parts.path, urlencode(cleaned), parts.fragment)
        )
        return ParsedPlaylist("xtream", host, stripped, username, password)

    return ParsedPlaylist("m3u", host, url, None, None)


def apply_parsed(playlist: Playlist, parsed: ParsedPlaylist) -> None:
    playlist.type = parsed.type
    playlist.host = parsed.host
    playlist.url = parsed.url
    playlist.username = parsed.username
    playlist.password_enc = encrypt_secret(parsed.password) if parsed.password else None


def playlist_url_for_app(playlist: Playlist) -> str:
    """Rebuilds the full URL (with the Xtream password) for delivery to the device."""
    if playlist.type != "xtream" or not playlist.password_enc:
        return playlist.url
    parts = urlsplit(playlist.url)
    query = [(k, v) for k, v in parse_qsl(parts.query, keep_blank_values=True)]
    query.append(("password", decrypt_secret(playlist.password_enc)))
    return urlunsplit((parts.scheme, parts.netloc, parts.path, urlencode(query), parts.fragment))


def replace_host(url: str, new_host: str) -> str:
    """Swaps scheme://netloc of `url` by `new_host` (used by the DNS migrator)."""
    parts = urlsplit(url)
    new = urlsplit(new_host)
    return urlunsplit((new.scheme, new.netloc, parts.path, parts.query, parts.fragment))


def normalize_host(raw: str) -> str:
    """Turns user input (`novo.com`, `http://novo.com/`, `HTTP://Novo.com:8080`) into
    `scheme://host[:port]`. Defaults to http when the scheme is missing."""
    value = raw.strip()
    if "://" not in value:
        value = "http://" + value
    parts = urlsplit(value)
    if parts.scheme not in ("http", "https") or not parts.hostname:
        raise bad_request(
            "DNS inválida. Informe algo como http://servidor.com:8080.", "invalid_host"
        )
    host = parts.hostname.lower()
    if parts.port:
        host = f"{host}:{parts.port}"
    return f"{parts.scheme}://{host}"
