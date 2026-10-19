"""The review tool's thin HTTP layer, once over a real socket on 127.0.0.1."""

import json
import threading
from collections.abc import Iterator
from pathlib import Path
from typing import Any
from urllib.error import HTTPError
from urllib.request import Request, urlopen

import pytest
from test_match import ORAS_LIST, vid
from test_review_service import Clock, LiveVideos, make, make_playlists

from vgmixr_pipeline.catalog.review.server import make_server


@pytest.fixture
def base(tmp_path: Path) -> Iterator[str]:
    server = make_server(make(tmp_path, Clock(), LiveVideos()), port=0)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    yield f"http://127.0.0.1:{server.server_address[1]}"
    server.shutdown()
    server.server_close()


def call(
    url: str, body: Any = None, content_type: str = "application/json", host: str | None = None
) -> tuple[int, Any]:
    data = None if body is None else json.dumps(body).encode()
    request = Request(url, data=data, method="GET" if body is None else "POST")
    request.add_header("Content-Type", content_type)
    if host:
        request.add_header("Host", host)
    try:
        with urlopen(request) as response:
            raw = response.read()
            kind = response.headers["Content-Type"]
            return response.status, json.loads(raw) if "json" in kind else raw.decode()
    except HTTPError as e:
        return e.code, json.loads(e.read())


def test_state_and_track_lists(base: str) -> None:
    status, state = call(f"{base}/api/state")
    assert status == 200 and state["families"][0]["id"] == "rse"
    status, rows = call(f"{base}/api/tracks?family=rse&status=review")
    assert [r["id"] for r in rows] == ["rse-route-101"]


def test_a_decision_is_written_and_an_invalid_one_refused(base: str) -> None:
    url = f"{base}/api/tracks/rse-route-101/streams"
    status, detail = call(url, {"accept": [{"videoId": vid(2)}]})
    assert (status, detail["status"]) == (200, "matched")
    status, error = call(url, {"accept": [{"videoId": "not an id"}]})
    assert status == 400 and error["errors"]


def test_unknown_paths_and_tracks(base: str) -> None:
    assert call(f"{base}/api/nope")[0] == 404
    assert call(f"{base}/api/tracks/rse-nope")[0] == 400
    assert call(f"{base}/static/..%2F..%2Fservice.py")[0] == 404


def test_writes_need_json_and_a_local_host(base: str) -> None:
    url = f"{base}/api/tracks/rse-route-101/streams"
    assert call(url, {"reject": [vid(2)]}, content_type="text/plain")[0] == 415
    assert call(url, {"reject": [vid(2)]}, host="evil.example:8765")[0] == 403


def test_the_page_is_served(base: str) -> None:
    status, page = call(f"{base}/")
    assert status == 200 and "<title>" in page
    status, script = call(f"{base}/static/review.js")
    assert status == 200 and 'api("state")' in script


def test_a_track_can_be_removed_and_restored(base: str) -> None:
    url = f"{base}/api/tracks/rse-abandoned-ship/exclude"
    assert call(url, {"removed": True})[1]["status"] == "excluded"
    assert call(url, {"removed": False})[1]["status"] == "unmatched"
    assert call(url, {"removed": "yes"})[0] == 400


def test_playlists_can_be_searched_previewed_added_and_removed(tmp_path: Path) -> None:
    server = make_server(make_playlists(tmp_path, Clock())[0], port=0)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    base = f"http://127.0.0.1:{server.server_address[1]}/api/playlists"
    try:
        status, coverage = call(base)
        assert status == 200 and coverage["families"][0]["id"] == "oras"
        status, found = call(f"{base}/search", {"family": "oras", "query": ""})
        assert status == 200 and found["results"][0]["id"] == ORAS_LIST
        body = {"playlist": ORAS_LIST, "games": ["omega-ruby", "alpha-sapphire"], "last": 2}
        status, preview = call(f"{base}/preview", body)
        assert status == 200 and preview["counts"]["oras"]["after"]["matched"] == 2
        status, added = call(f"{base}/add", {**body, "note": "ORAS OST"})
        assert status == 200 and added["families"][0]["id"] == "rse"
        status, removal = call(f"{base}/preview-removal", {"index": 1, "playlist": ORAS_LIST})
        assert status == 200 and removal["counts"]["oras"]["after"]["matched"] == 0
        assert call(f"{base}/remove", {"index": 1, "playlist": ORAS_LIST})[0] == 200
        assert call(f"{base}/remove", {"index": 1, "playlist": ORAS_LIST})[0] == 400
        assert call(f"{base}/preview", {**body, "last": "2"})[0] == 400
        assert call(f"{base}/remove", {"index": "1", "playlist": ORAS_LIST})[0] == 400
    finally:
        server.shutdown()
        server.server_close()


def test_a_tracks_games_can_be_set(base: str) -> None:
    url = f"{base}/api/tracks/rse-littleroot-town/games"
    status, detail = call(url, {"games": ["emerald"]})
    assert (status, detail["games"], detail["gamesOverride"]) == (200, ["emerald"], True)
    assert call(url, {"games": ["omega-ruby"]})[0] == 400
    status, detail = call(url, {"games": None})
    assert (status, detail["gamesOverride"]) == (200, False)
