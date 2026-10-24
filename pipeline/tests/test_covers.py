"""Family covers: hand-cropped 16:9 images converted into catalog/covers/<family>.webp."""

from pathlib import Path

import pytest
from PIL import Image

from vgmixr_pipeline.covers import COVER_SIZE, CoverError, convert_covers, family_ids

MANIFEST = {
    "games": [
        {"id": "ruby", "family": "rse"},
        {"id": "sapphire", "family": "rse"},
        {"id": "x", "family": "xy"},
        {"id": "yellow"},
    ]
}


def save(path: Path, size: tuple[int, int], color: str = "red") -> None:
    Image.new("RGB", size, color).save(path)


@pytest.fixture
def dirs(tmp_path: Path) -> tuple[Path, Path]:
    src, out = tmp_path / "in", tmp_path / "covers"
    src.mkdir()
    return src, out


def test_family_ids_include_single_game_families() -> None:
    assert family_ids(MANIFEST) == {"rse", "xy", "yellow"}


def test_a_16_9_image_becomes_a_webp_of_the_cover_size(dirs: tuple[Path, Path]) -> None:
    src, out = dirs
    save(src / "rse.jpg", (1920, 1080))

    result = convert_covers(src, out, {"rse", "xy"})

    with Image.open(out / "rse.webp") as image:
        assert image.format == "WEBP"
        assert image.size == COVER_SIZE == (1280, 720)
    assert result.written == ["rse"]
    assert result.missing == ["xy"]


def test_any_common_format_and_alpha_is_accepted(dirs: tuple[Path, Path]) -> None:
    src, out = dirs
    Image.new("RGBA", (640, 360), (0, 0, 255, 128)).save(src / "xy.png")
    save(src / "yellow.webp", (320, 180))

    convert_covers(src, out, {"xy", "yellow"})

    assert (out / "xy.webp").exists()
    assert (out / "yellow.webp").exists()


def test_a_name_that_is_not_a_family_fails_and_writes_nothing(dirs: tuple[Path, Path]) -> None:
    src, out = dirs
    save(src / "rse.jpg", (640, 360))
    save(src / "rsee.jpg", (640, 360))

    with pytest.raises(CoverError, match="rsee"):
        convert_covers(src, out, {"rse"})
    assert not (out / "rse.webp").exists()


def test_an_image_that_is_not_16_9_fails(dirs: tuple[Path, Path]) -> None:
    src, out = dirs
    save(src / "rse.jpg", (512, 512))

    with pytest.raises(CoverError, match=r"rse\.jpg.*512x512"):
        convert_covers(src, out, {"rse"})


def test_a_crop_a_few_pixels_off_16_9_is_accepted(dirs: tuple[Path, Path]) -> None:
    src, out = dirs
    save(src / "rse.jpg", (1600, 905))

    convert_covers(src, out, {"rse"})

    with Image.open(out / "rse.webp") as image:
        assert image.size == COVER_SIZE


def test_two_images_for_one_family_fail(dirs: tuple[Path, Path]) -> None:
    src, out = dirs
    save(src / "rse.jpg", (640, 360))
    save(src / "rse.png", (640, 360))

    with pytest.raises(CoverError, match="rse"):
        convert_covers(src, out, {"rse"})


def test_a_file_that_is_not_an_image_fails(dirs: tuple[Path, Path]) -> None:
    src, out = dirs
    (src / "rse.jpg").write_text("not an image")

    with pytest.raises(CoverError, match=r"rse\.jpg"):
        convert_covers(src, out, {"rse"})


def test_a_rerun_writes_identical_bytes(dirs: tuple[Path, Path]) -> None:
    src, out = dirs
    save(src / "rse.jpg", (800, 450))
    convert_covers(src, out, {"rse"})
    first = (out / "rse.webp").read_bytes()

    convert_covers(src, out, {"rse"})

    assert (out / "rse.webp").read_bytes() == first


def test_covers_without_a_family_are_reported(dirs: tuple[Path, Path]) -> None:
    src, out = dirs
    out.mkdir()
    save(out / "gone.webp", COVER_SIZE)

    result = convert_covers(src, out, {"rse"})

    assert result.orphans == ["gone"]
