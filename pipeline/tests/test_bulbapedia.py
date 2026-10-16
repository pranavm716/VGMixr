import json
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

import pytest

from vgmixr_pipeline.catalog.bulbapedia import Bulbapedia

PAGE = "Pokémon Ruby & Pokémon Sapphire: Super Music Collection"


class FakeApi:
    def __init__(self, wikitext: str = "==Tracklist==") -> None:
        self.urls: list[str] = []
        self.wikitext = wikitext

    def get(self, url: str) -> bytes:
        self.urls.append(url)
        page = parse_qs(urlsplit(url).query)["page"][0]
        body = {"parse": {"title": page, "pageid": 1, "revid": 42, "wikitext": self.wikitext}}
        return json.dumps(body).encode()


def client(tmp_path: Path, api: FakeApi, refresh: bool = False) -> tuple[Bulbapedia, list[float]]:
    sleeps: list[float] = []
    bulbapedia = Bulbapedia(tmp_path, get=api.get, refresh=refresh, sleep=sleeps.append)
    return bulbapedia, sleeps


def test_requests_the_page_wikitext(tmp_path: Path) -> None:
    api = FakeApi("text")
    bulbapedia, _ = client(tmp_path, api)
    assert bulbapedia.wikitext(PAGE) == "text"
    url = urlsplit(api.urls[0])
    assert (
        f"{url.scheme}://{url.netloc}{url.path}" == "https://bulbapedia.bulbagarden.net/w/api.php"
    )
    assert parse_qs(url.query) == {
        "action": ["parse"],
        "page": [PAGE],
        "prop": ["wikitext|revid"],
        "redirects": ["1"],
        "format": ["json"],
        "formatversion": ["2"],
    }


def test_caches_pages_on_disk(tmp_path: Path) -> None:
    api = FakeApi("text")
    client(tmp_path, api)[0].wikitext(PAGE)
    cached = json.loads((tmp_path / "pokemon-ruby-and-pokemon-sapphire-super-music-collection.json")
                        .read_text(encoding="utf-8"))  # fmt: skip
    assert cached == {"title": PAGE, "revid": 42, "wikitext": "text"}

    later = FakeApi("changed")
    assert client(tmp_path, later)[0].wikitext(PAGE) == "text"
    assert later.urls == []


def test_refresh_fetches_again_once_per_run(tmp_path: Path) -> None:
    client(tmp_path, FakeApi("old"))[0].wikitext(PAGE)
    api = FakeApi("new")
    bulbapedia, _ = client(tmp_path, api, refresh=True)
    assert bulbapedia.wikitext(PAGE) == "new"
    assert bulbapedia.wikitext(PAGE) == "new"
    assert len(api.urls) == 1


def test_waits_between_network_requests(tmp_path: Path) -> None:
    bulbapedia, sleeps = client(tmp_path, FakeApi())
    bulbapedia.wikitext("A")
    bulbapedia.wikitext("A")  # cached
    bulbapedia.wikitext("B")
    assert sleeps == [1.0]


def test_api_errors_raise(tmp_path: Path) -> None:
    def get(url: str) -> bytes:
        return b'{"error": {"code": "missingtitle", "info": "The page does not exist."}}'

    with pytest.raises(RuntimeError, match="missingtitle"):
        Bulbapedia(tmp_path, get=get).wikitext("Nope")
    assert list(tmp_path.iterdir()) == []
