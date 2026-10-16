"""The scoring weights against hand-labeled candidates (tests/data/youtube/labeled.json).

Each row is a real track and a real YouTube candidate, labeled good (the track's audio) or bad.
Rows were sampled from a real run, stratified to cover remakes, red flags and near-threshold
scores. When a weight changes, these must still hold.
"""

import json
from pathlib import Path
from typing import Any

import pytest

from vgmixr_pipeline.catalog.config import load_games
from vgmixr_pipeline.catalog.score import Candidate, Names, TrackFacts, Verdict, classify, score

LABELED = Path(__file__).parent / "data" / "youtube" / "labeled.json"


def load() -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = json.loads(LABELED.read_text(encoding="utf-8"))["rows"]
    return rows


@pytest.fixture(scope="module")
def verdicts() -> list[tuple[dict[str, Any], Verdict]]:
    games = load_games()
    names = Names.build(games.games, games.youtube.aliases)
    result = []
    for row in load():
        t, c = row["track"], row["candidate"]
        facts = TrackFacts(family=t["family"], games=frozenset(t["games"]), title=t["title"],
                           duration_ms=t["durationMs"], position=t["position"],
                           game_boy=t["gameBoy"])  # fmt: skip
        candidate = Candidate(c["videoId"], c["title"], c["durationMs"], c["channel"], c["kind"],
                              frozenset(c["games"]), c["position"], c["startMs"], c["endMs"],
                              frozenset(c["noise"]))  # fmt: skip
        result.append((row, classify(score(facts, candidate, names).value)))
    return result


def describe(rows: list[dict[str, Any]]) -> str:
    return "\n".join(f"{r['track']['id']}: {r['candidate']['title']} ({r['reason']})" for r in rows)


def test_the_set_is_big_enough_and_has_both_labels() -> None:
    labels = [row["label"] for row in load()]
    assert len(labels) >= 150
    assert set(labels) == {"good", "bad"}
    assert labels.count("bad") >= 50


def test_no_bad_candidate_is_accepted(verdicts: list[tuple[dict[str, Any], Verdict]]) -> None:
    accepted = [r for r, v in verdicts if r["label"] == "bad" and v is Verdict.ACCEPT]
    assert not accepted, describe(accepted)


def test_most_good_candidates_are_accepted(
    verdicts: list[tuple[dict[str, Any], Verdict]],
) -> None:
    good = [(r, v) for r, v in verdicts if r["label"] == "good"]
    accepted = sum(v is Verdict.ACCEPT for _, v in good)
    assert accepted >= 0.75 * len(good), f"{accepted} of {len(good)} good accepted"


def test_few_good_candidates_are_rejected(
    verdicts: list[tuple[dict[str, Any], Verdict]],
) -> None:
    good = [r for r, v in verdicts if r["label"] == "good"]
    rejected = [r for r, v in verdicts if r["label"] == "good" and v is Verdict.REJECT]
    assert len(rejected) <= 0.10 * len(good), describe(rejected)
