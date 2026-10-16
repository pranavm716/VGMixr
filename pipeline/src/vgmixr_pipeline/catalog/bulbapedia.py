"""Page wikitext from Bulbapedia's MediaWiki API, cached on disk.

A cached page is never refetched unless `refresh` is set, so builds after the first run offline
and see the same text. Requests are spaced out to stay polite to the wiki.
"""

import json
import time
import urllib.request
from collections.abc import Callable
from pathlib import Path
from typing import Any
from urllib.parse import urlencode

from vgmixr_pipeline.catalog.slug import slugify

API = "https://bulbapedia.bulbagarden.net/w/api.php"
USER_AGENT = "VGMixr-pipeline/0.1 (personal, non-commercial soundtrack catalog builder)"
DELAY_SECONDS = 1.0

type Get = Callable[[str], bytes]


def urllib_get(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        body: bytes = response.read()
        return body


class Bulbapedia:
    def __init__(
        self,
        cache_dir: Path,
        get: Get = urllib_get,
        refresh: bool = False,
        sleep: Callable[[float], Any] = time.sleep,
    ) -> None:
        self._cache_dir = cache_dir
        self._get = get
        self._refresh = refresh
        self._sleep = sleep
        self._fetched: set[str] = set()  # pages fetched in this run

    def wikitext(self, page: str) -> str:
        path = self._cache_dir / f"{slugify(page)}.json"
        if path.exists() and (not self._refresh or page in self._fetched):
            cached: dict[str, Any] = json.loads(path.read_text(encoding="utf-8"))
            return str(cached["wikitext"])
        if self._fetched:
            self._sleep(DELAY_SECONDS)
        parsed = self._fetch(page)
        self._fetched.add(page)
        self._cache_dir.mkdir(parents=True, exist_ok=True)
        entry = {"title": parsed["title"], "revid": parsed["revid"], "wikitext": parsed["wikitext"]}
        path.write_text(json.dumps(entry, ensure_ascii=False, indent=1), encoding="utf-8")
        return str(parsed["wikitext"])

    def _fetch(self, page: str) -> dict[str, Any]:
        query = {
            "action": "parse",
            "page": page,
            "prop": "wikitext|revid",
            "redirects": "1",
            "format": "json",
            "formatversion": "2",
        }
        body: dict[str, Any] = json.loads(self._get(f"{API}?{urlencode(query)}"))
        if "error" in body:
            error = body["error"]
            raise RuntimeError(f"Bulbapedia page {page!r}: {error['code']}: {error['info']}")
        parsed: dict[str, Any] = body["parse"]
        return parsed
