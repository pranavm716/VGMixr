import json
from pathlib import Path
from typing import Any

from vgmixr_pipeline.catalog.youtube import Chapter, Entry, Video, YouTube

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
    assert youtube.video("aaaaaaaaaaa") == Video("aaaaaaaaaaa", "", None, [])
    assert fake.calls == []
