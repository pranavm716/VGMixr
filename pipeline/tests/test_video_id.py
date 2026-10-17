"""Pasted YouTube links are reduced to video ids; only the id is ever stored."""

import pytest

from vgmixr_pipeline.catalog.review.video_id import playlist_id_from, video_id_from

ID = "ID2Iv5HP-0I"


@pytest.mark.parametrize(
    "text",
    [
        ID,
        f"  {ID}\n",
        f"https://www.youtube.com/watch?v={ID}",
        f"https://www.youtube.com/watch?v={ID}&list=PL2uxd6YWj7PIOlswxbt16G63Klr_3lsbR&index=3",
        f"https://youtube.com/watch?feature=shared&v={ID}&t=42s",
        f"www.youtube.com/watch?v={ID}",
        f"https://m.youtube.com/watch?v={ID}",
        f"https://music.youtube.com/watch?v={ID}&si=abc",
        f"https://youtu.be/{ID}",
        f"https://youtu.be/{ID}?si=xyz&t=10",
        f"https://www.youtube.com/shorts/{ID}",
        f"https://www.youtube.com/embed/{ID}?start=5",
        f"https://www.youtube-nocookie.com/embed/{ID}",
        f"https://www.youtube.com/live/{ID}",
    ],
)
def test_links_and_bare_ids_give_the_id(text: str) -> None:
    assert video_id_from(text) == ID


@pytest.mark.parametrize(
    "text",
    [
        "",
        "ID2Iv5HP-0",
        "ID2Iv5HP-0I0",
        "https://www.youtube.com/playlist?list=PL2uxd6YWj7PIOlswxbt16G63Klr_3lsbR",
        f"https://example.com/watch?v={ID}",
        "https://www.youtube.com/watch?v=not-an-id",
        "https://youtu.be/",
    ],
)
def test_anything_else_is_none(text: str) -> None:
    assert video_id_from(text) is None


LIST = "PL2uxd6YWj7PIOlswxbt16G63Klr_3lsbR"


@pytest.mark.parametrize(
    "text",
    [
        LIST,
        f" {LIST}\n",
        f"https://www.youtube.com/playlist?list={LIST}",
        f"youtube.com/playlist?list={LIST}&si=abc",
        f"https://www.youtube.com/watch?v={ID}&list={LIST}&index=3",
        f"https://music.youtube.com/playlist?list={LIST}",
    ],
)
def test_playlist_links_and_bare_ids_give_the_playlist_id(text: str) -> None:
    assert playlist_id_from(text) == LIST


@pytest.mark.parametrize(
    "text",
    [
        "",
        "short",
        f"https://www.youtube.com/watch?v={ID}",
        f"https://example.com/playlist?list={LIST}",
        "https://www.youtube.com/playlist?list=bad!",
    ],
)
def test_anything_else_is_no_playlist(text: str) -> None:
    assert playlist_id_from(text) is None
