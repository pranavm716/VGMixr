"""The review tool's HTTP layer: routes requests to Review's handlers and serves the page.

Single-threaded on purpose, so requests (and the file writes they make) never overlap. Bound to
127.0.0.1; writes need a JSON body and a local Host header, so another site open in the browser
can't post to it (a JSON post needs a CORS preflight, which this server never answers).
"""

import json
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, unquote, urlsplit

from vgmixr_pipeline.catalog.review.service import Review, ReviewError

STATIC_DIR = Path(__file__).parent / "static"
_TYPES = {".html": "text/html", ".js": "text/javascript", ".css": "text/css"}
_LOCAL_HOSTS = {"127.0.0.1", "localhost"}


class _HttpError(Exception):
    def __init__(self, status: int, message: str) -> None:
        super().__init__(message)
        self.status = status


def make_server(review: Review, port: int) -> HTTPServer:
    handler = type("Handler", (_Handler,), {"review": review})
    return HTTPServer(("127.0.0.1", port), handler)


class _Handler(BaseHTTPRequestHandler):
    review: Review

    def do_GET(self) -> None:
        self._handle("GET")

    def do_POST(self) -> None:
        self._handle("POST")

    def log_message(self, format: str, *args: Any) -> None:
        pass  # quiet; errors reach the page

    def _handle(self, method: str) -> None:
        url = urlsplit(self.path)
        parts = [unquote(p) for p in url.path.split("/") if p]
        try:
            if parts[:1] == ["api"]:
                self._json(200, self._api(method, parts[1:], url.query))
            elif method == "GET":
                self._static(parts)
            else:
                raise _HttpError(404, f"no such path: {url.path}")
        except ReviewError as e:
            self._json(400, {"errors": e.errors})
        except _HttpError as e:
            self._json(e.status, {"errors": [str(e)]})

    def _api(self, method: str, parts: list[str], query: str) -> Any:
        r = self.review
        if method == "GET":
            args = {k: v[0] for k, v in parse_qs(query).items()}
            match parts:
                case ["state"]:
                    return r.state()
                case ["tracks"]:
                    return r.tracks(args.get("family"), args.get("status"))
                case ["tracks", track_id]:
                    return r.track(track_id)
                case ["playlists"]:
                    return r.playlists()
        else:
            body = self._body()
            match parts:
                case ["tracks", track_id, "streams"]:
                    return r.set_streams(track_id, body)
                case ["tracks", track_id, "exclude"]:
                    removed = body.get("removed")
                    if not isinstance(removed, bool):
                        raise ReviewError(["removed must be true or false"])
                    return r.exclude(track_id, removed)
                case ["tracks", track_id, "search"]:
                    return r.search(track_id, str(body.get("query") or ""))
                case ["tracks", track_id, "video"]:
                    return r.video(track_id, str(body.get("text") or ""),
                                   _int(body.get("startMs")), _int(body.get("endMs")))  # fmt: skip
                case ["playlists", "search"]:
                    return r.search_playlists(str(body.get("family") or ""),
                                              str(body.get("query") or ""))  # fmt: skip
                case ["playlists", "preview"]:
                    return r.preview_playlist(*_playlist(body))
                case ["playlists", "add"]:
                    return r.add_playlist(*_playlist(body), str(body.get("note") or "") or None)
                case ["playlists", "preview-removal"]:
                    return r.preview_removal(_index(body), str(body.get("playlist") or ""))
                case ["playlists", "remove"]:
                    return r.remove_playlist(_index(body), str(body.get("playlist") or ""))
                case ["rebuild"]:
                    return r.rebuild()
                case ["youtube", "retry"]:
                    return r.retry_youtube()
        raise _HttpError(404, f"no such endpoint: {method} /api/{'/'.join(parts)}")

    def _body(self) -> dict[str, Any]:
        if (self.headers.get("Host") or "").rsplit(":", 1)[0] not in _LOCAL_HOSTS:
            raise _HttpError(403, "writes are only accepted from this machine")
        if (self.headers.get("Content-Type") or "").split(";")[0] != "application/json":
            raise _HttpError(415, "send JSON")
        raw = self.rfile.read(int(self.headers.get("Content-Length") or 0))
        try:
            body = json.loads(raw or b"{}")
        except ValueError as e:
            raise _HttpError(400, f"invalid JSON: {e}") from e
        if not isinstance(body, dict):
            raise _HttpError(400, "send a JSON object")
        return body

    def _static(self, parts: list[str]) -> None:
        name = "index.html" if not parts else parts[1] if parts[0] == "static" else None
        files = {p.name: p for p in STATIC_DIR.iterdir()}
        if name is None or len(parts) > 2 or name not in files:
            raise _HttpError(404, f"no such file: {'/'.join(parts)}")
        path = files[name]
        self._send(200, _TYPES.get(path.suffix, "application/octet-stream"), path.read_bytes())

    def _json(self, status: int, data: Any) -> None:
        self._send(status, "application/json", json.dumps(data, ensure_ascii=False).encode())

    def _send(self, status: int, kind: str, body: bytes) -> None:
        self.send_response(status)
        self.send_header("Content-Type", f"{kind}; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)


def _int(value: Any) -> int | None:
    if value is None or value == "":
        return None
    if isinstance(value, bool) or not isinstance(value, int):
        raise ReviewError([f"{value!r} is not a whole number"])
    return value


def _playlist(body: dict[str, Any]) -> tuple[str, Any, int | None, int | None]:
    """A playlist entry's fields; the service validates games and the range."""
    playlist = str(body.get("playlist") or "")
    return playlist, body.get("games"), _int(body.get("first")), _int(body.get("last"))


def _index(body: dict[str, Any]) -> int:
    index = _int(body.get("index"))
    if index is None:
        raise ReviewError(["index is missing"])
    return index
