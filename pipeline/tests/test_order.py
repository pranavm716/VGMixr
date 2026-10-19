"""A family's hand-set track order (pipeline/data/order.json) applied to its tracks."""

from vgmixr_pipeline.catalog.order import ordered

DEFAULT = ["a", "b", "c", "d", "e"]


def test_listed_ids_keep_the_lists_order() -> None:
    assert ordered(DEFAULT, ["e", "d", "c", "b", "a"]) == ["e", "d", "c", "b", "a"]


def test_without_a_list_the_default_order_stays() -> None:
    assert ordered(DEFAULT, []) == DEFAULT


def test_an_unlisted_id_goes_right_after_the_listed_id_before_it() -> None:
    # "c" follows "b" in track-list order, so it stays right after "b" wherever "b" moved.
    assert ordered(DEFAULT, ["b", "a", "e", "d"]) == ["b", "c", "a", "e", "d"]


def test_unlisted_ids_before_every_listed_one_go_first() -> None:
    assert ordered(DEFAULT, ["d", "c", "e"]) == ["a", "b", "d", "c", "e"]


def test_a_run_of_unlisted_ids_keeps_its_default_order() -> None:
    assert ordered(DEFAULT, ["e", "a"]) == ["e", "a", "b", "c", "d"]


def test_listed_ids_that_are_not_tracks_are_skipped() -> None:
    assert ordered(["a", "b", "c"], ["c", "gone", "a", "b"]) == ["c", "a", "b"]
