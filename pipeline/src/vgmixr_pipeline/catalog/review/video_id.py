"""Reduces a pasted YouTube link (or a bare id) to its video id. URLs are never stored."""

from urllib.parse import parse_qs, urlsplit

from vgmixr_pipeline.manifest.validate import VIDEO_ID

_HOSTS = {"youtube.com", "m.youtube.com", "music.youtube.com", "youtube-nocookie.com"}
# Paths whose next segment is the id: /shorts/ID, /embed/ID, /live/ID.
_ID_PATHS = {"shorts", "embed", "live"}


def video_id_from(text: str) -> str | None:
    text = text.strip()
    if VIDEO_ID.fullmatch(text):
        return text
    url = urlsplit(text if "//" in text else "https://" + text)
    host = (url.hostname or "").removeprefix("www.")
    segments = [s for s in url.path.split("/") if s]
    candidate: str | None = None
    if host == "youtu.be" and segments:
        candidate = segments[0]
    elif host in _HOSTS and segments == ["watch"]:
        candidate = parse_qs(url.query).get("v", [None])[0]
    elif host in _HOSTS and len(segments) == 2 and segments[0] in _ID_PATHS:
        candidate = segments[1]
    return candidate if candidate is not None and VIDEO_ID.fullmatch(candidate) else None
