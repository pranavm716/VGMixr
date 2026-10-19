"""A family's hand-set track order (pipeline/data/order.json, written by the review tool).

The order is a list of track ids. A track missing from it (a new track list, a split) goes right
after the listed track that precedes it in track-list order, so it lands next to its neighbours.
"""

from collections.abc import Collection


def ordered(default: list[str], listed: Collection[str]) -> list[str]:
    """`default` (ids in track-list order) rearranged by `listed`; listed ids that aren't in
    `default` are skipped."""
    present = set(default)
    placed = [i for i in listed if i in present]
    is_listed = set(placed)
    # Each unlisted id follows the last listed id before it in `default` (None: before all).
    after: dict[str | None, list[str]] = {}
    anchor: str | None = None
    for id_ in default:
        if id_ in is_listed:
            anchor = id_
        else:
            after.setdefault(anchor, []).append(id_)
    result = list(after.get(None, []))
    for id_ in placed:
        result += [id_, *after.get(id_, [])]
    return result
