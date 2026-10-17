import json
from pathlib import Path
from typing import Any

import pytest

from vgmixr_pipeline.catalog.youtube import (
    PLAYLIST_RESULTS,
    Chapter,
    Entry,
    PlaylistHit,
    Video,
    YouTube,
    YouTubeBlocked,
    raise_if_blocked,
)

PLAYLIST = "PLabcdefghij"


def entry(id_: str, title: str, duration: float | None = 60, **extra: Any) -> dict[str, Any]:
    return {"_type": "url", "id": id_, "title": title, "duration": duration,
            "channel": "Uploader", "view_count": 10, "thumbnails": [{}], **extra}  # fmt: skip


class FakeYtDlp:
    def __init__(self, info: dict[str, Any]) -> None:
        self.calls: list[tuple[str, bool]] = []
        self.info = info

    def extract(self, url: str, flat: bool) -> dict[str, Any]:
        self.calls.append((url, flat))
        return self.info


def client(tmp_path: Path, fake: FakeYtDlp, refresh: bool = False) -> tuple[YouTube, list[float]]:
    sleeps: list[float] = []
    return YouTube(tmp_path, extract=fake.extract, refresh=refresh, sleep=sleeps.append), sleeps


def listing(*entries: dict[str, Any]) -> dict[str, Any]:
    return {"_type": "playlist", "id": "x", "title": "List", "entries": list(entries)}


def test_playlist_reads_flat_entries_with_their_positions(tmp_path: Path) -> None:
    fake = FakeYtDlp(listing(
        entry("aaaaaaaaaaa", "Opening", 61.5),
        entry("bbbbbbbbbbb", "[Deleted video]", None),
        entry("ccccccccccc", "[Private video]", None),
        entry("ddddddddddd", "Route 101", None),
    ))  # fmt: skip
    youtube, _ = client(tmp_path, fake)
    assert youtube.playlist(PLAYLIST) == [
        Entry("aaaaaaaaaaa", "Opening", 61_500, "Uploader", 1),
        Entry("ddddddddddd", "Route 101", None, "Uploader", 4),
    ]
    assert fake.calls == [(f"https://www.youtube.com/playlist?list={PLAYLIST}", True)]


def test_responses_are_cached_trimmed(tmp_path: Path) -> None:
    fake = FakeYtDlp(listing(entry("aaaaaaaaaaa", "Opening")))
    client(tmp_path, fake)[0].playlist(PLAYLIST)
    cached = json.loads((tmp_path / "playlists" / f"{PLAYLIST}.json").read_text(encoding="utf-8"))
    assert cached == {
        "entries": [
            {"id": "aaaaaaaaaaa", "title": "Opening", "duration": 60, "channel": "Uploader"}
        ]
    }

    later = FakeYtDlp(listing())
    assert client(tmp_path, later)[0].playlist(PLAYLIST)[0].title == "Opening"
    assert later.calls == []


def test_refresh_fetches_again_once_per_run(tmp_path: Path) -> None:
    client(tmp_path, FakeYtDlp(listing(entry("aaaaaaaaaaa", "Old"))))[0].playlist(PLAYLIST)
    fake = FakeYtDlp(listing(entry("aaaaaaaaaaa", "New")))
    youtube, _ = client(tmp_path, fake, refresh=True)
    assert youtube.playlist(PLAYLIST)[0].title == "New"
    assert youtube.playlist(PLAYLIST)[0].title == "New"
    assert len(fake.calls) == 1


def test_sleeps_between_network_calls_only(tmp_path: Path) -> None:
    fake = FakeYtDlp(listing(entry("aaaaaaaaaaa", "Opening")))
    youtube, sleeps = client(tmp_path, fake)
    youtube.playlist(PLAYLIST)
    youtube.playlist(PLAYLIST)  # cached
    assert sleeps == []
    youtube.playlist("PLbcdefghijk")
    assert sleeps == [1.0]


def test_search_takes_the_top_results(tmp_path: Path) -> None:
    fake = FakeYtDlp(listing(entry("aaaaaaaaaaa", "Route 101"), entry("bbbbbbbbbbb", "Remix")))
    youtube, _ = client(tmp_path, fake)
    results = youtube.search("Pokémon Ruby Route 101", n=10)
    assert [r.id for r in results] == ["aaaaaaaaaaa", "bbbbbbbbbbb"]
    assert fake.calls == [("ytsearch10:Pokémon Ruby Route 101", True)]
    assert (tmp_path / "search" / "pokemon-ruby-route-101.json").exists()


def test_playlist_search_lists_playlists_from_the_results_page(tmp_path: Path) -> None:
    fake = FakeYtDlp(listing(
        entry("PLaaaaaaaaaa", "Diamond & Pearl OST", None, ie_key="YoutubeTab"),
        entry("PLbbbbbbbbbb", "DPPt Soundtrack", None, ie_key="YoutubeTab"),
    ))  # fmt: skip
    youtube, _ = client(tmp_path, fake)
    assert youtube.search_playlists("Pokémon Diamond Pearl soundtrack") == [
        PlaylistHit("PLaaaaaaaaaa", "Diamond & Pearl OST", "Uploader"),
        PlaylistHit("PLbbbbbbbbbb", "DPPt Soundtrack", "Uploader"),
    ]
    assert fake.calls == [(
        "https://www.youtube.com/results?search_query=Pok%C3%A9mon+Diamond+Pearl+soundtrack"
        "&sp=EgIQAw%3D%3D",
        True,
    )]  # fmt: skip
    cached = tmp_path / "playlist-search" / "pokemon-diamond-pearl-soundtrack.json"
    assert json.loads(cached.read_text(encoding="utf-8"))["entries"][0] == {
        "id": "PLaaaaaaaaaa", "title": "Diamond & Pearl OST", "channel": "Uploader"
    }  # fmt: skip

    later = FakeYtDlp(listing())
    assert len(client(tmp_path, later)[0].search_playlists("Pokémon Diamond Pearl soundtrack")) == 2
    assert later.calls == []


def test_playlist_search_keeps_the_top_results(tmp_path: Path) -> None:
    hits = [entry(f"PL{i:010d}", f"List {i}", None) for i in range(PLAYLIST_RESULTS + 5)]
    youtube, _ = client(tmp_path, FakeYtDlp(listing(*hits)))
    assert len(youtube.search_playlists("Pokémon X Y soundtrack")) == PLAYLIST_RESULTS
    assert len(youtube.search_playlists("Pokémon X Y OST", n=3)) == 3


def test_video_reads_chapters_and_the_last_one_plays_to_the_end(tmp_path: Path) -> None:
    fake = FakeYtDlp({
        "id": "aaaaaaaaaaa", "title": "Full OST", "duration": 300, "description": "0:00 A",
        "formats": [],
        "chapters": [
            {"start_time": 0.0, "end_time": 100.0, "title": "01. Opening"},
            {"start_time": 100.0, "end_time": 300.0, "title": "02. Route 101"},
        ],
    })  # fmt: skip
    youtube, _ = client(tmp_path, fake)
    assert youtube.video("aaaaaaaaaaa") == Video(
        "aaaaaaaaaaa",
        "Full OST",
        300_000,
        [Chapter("01. Opening", 0, 100_000), Chapter("02. Route 101", 100_000, None)],
    )
    assert fake.calls == [("https://www.youtube.com/watch?v=aaaaaaaaaaa", False)]


def test_video_without_chapters(tmp_path: Path) -> None:
    fake = FakeYtDlp({"id": "aaaaaaaaaaa", "title": "Song", "duration": 90, "chapters": None})
    assert client(tmp_path, fake)[0].video("aaaaaaaaaaa").chapters == []


def test_cached_only_reads_the_cache_and_treats_the_rest_as_empty(tmp_path: Path) -> None:
    client(tmp_path, FakeYtDlp(listing(entry("aaaaaaaaaaa", "Opening"))))[0].playlist(PLAYLIST)
    fake = FakeYtDlp(listing(entry("bbbbbbbbbbb", "New")))
    youtube = YouTube(tmp_path, extract=fake.extract, cached_only=True)
    assert youtube.playlist(PLAYLIST)[0].title == "Opening"
    assert youtube.playlist("PLbcdefghijk") == []
    assert youtube.search("Pokémon Ruby Route 101") == []
    assert youtube.search_playlists("Pokémon Ruby soundtrack") == []
    assert youtube.video("aaaaaaaaaaa") == Video("aaaaaaaaaaa", "", None, [])
    assert fake.calls == []


BOT_CHECK = (
    "ERROR: [youtube] aaaaaaaaaaa: Sign in to confirm you’re not a bot. Use --cookies-from-browser"
)


def test_the_bot_check_is_told_apart_from_other_errors() -> None:
    with pytest.raises(YouTubeBlocked):
        raise_if_blocked(Exception(BOT_CHECK))
    raise_if_blocked(Exception("ERROR: [youtube] aaaaaaaaaaa: Video unavailable"))


def test_a_block_is_raised_and_nothing_is_cached(tmp_path: Path) -> None:
    def blocked(url: str, flat: bool) -> dict[str, Any]:
        raise YouTubeBlocked(BOT_CHECK)

    with pytest.raises(YouTubeBlocked):
        YouTube(tmp_path, extract=blocked).search("Pokémon Ruby Route 101")
    assert list(tmp_path.rglob("*.json")) == []


def test_delay_sets_the_pause_between_network_calls(tmp_path: Path) -> None:
    fake = FakeYtDlp(listing(entry("aaaaaaaaaaa", "Opening")))
    sleeps: list[float] = []
    youtube = YouTube(tmp_path, extract=fake.extract, sleep=sleeps.append, delay=0.0)
    youtube.playlist(PLAYLIST)
    youtube.playlist("PLbcdefghijk")
    assert sleeps == []
