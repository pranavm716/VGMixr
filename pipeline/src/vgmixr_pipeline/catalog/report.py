"""The reports. The family report: per family, shared vs. exclusive tracks and exclusions, used
to confirm family groupings (e.g. SM/USUM), to find tracks that need an override, and ids.json
entries whose source key vanished (a renamed upstream title or a merge; step 12e). The match
report: per family, how many tracks got stream sources, which need review and which found nothing
(and so are left out of the manifest).
"""

from vgmixr_pipeline.catalog.build import BuildResult, DraftTrack, Excluded
from vgmixr_pipeline.catalog.match import Match
from vgmixr_pipeline.manifest.models import Game


def family_report(games: list[Game], result: BuildResult) -> str:
    sections = ["# Family report", ""]
    for family, members in _families(games).items():
        tracks = [t for t in result.tracks if t.family == family]
        excluded = [e for e in result.excluded if e.family == family]
        names = " / ".join(g.name for g in members)
        sections += [f"## {family}: {names}", ""]
        if not tracks:
            sections.append("- **No tracks.**")
        else:
            shared = sum(t.games is None for t in tracks)
            sections.append(f"- Tracks: {len(tracks)} (shared by all games: {shared})")
            for key, group in _exclusive_groups(tracks, [g.id for g in members]):
                titles = ", ".join(t.title for t in group)
                sections.append(f"- Exclusive to {key}: {len(group)} ({titles})")
        sections += _excluded_lines(excluded)
        vanished = [v for v in result.vanished if v.family == family]
        if vanished:
            pairs = ", ".join(f"{v.key} -> {v.id}" for v in vanished)
            sections.append(f"- Vanished ids: {len(vanished)} ({pairs})")
        sections.append("")
    return "\n".join(sections)


def summary(games: list[Game], result: BuildResult) -> str:
    lines = ["family  tracks  shared  exclusive  excluded"]
    for family in _families(games):
        tracks = [t for t in result.tracks if t.family == family]
        shared = sum(t.games is None for t in tracks)
        excluded = sum(e.family == family for e in result.excluded)
        lines.append(
            f"{family:<6}{len(tracks):>4} tracks{shared:>4} shared{len(tracks) - shared:>4} "
            f"exclusive{excluded:>4} excluded"
        )
    if result.vanished:
        lines.append(f"{len(result.vanished)} vanished ids (see family-report.md)")
    return "\n".join(lines)


def match_report(games: list[Game], matches: list[Match]) -> str:
    sections = ["# Match report", ""]
    for family, members in _families(games).items():
        names = " / ".join(g.name for g in members)
        sections += [f"## {family}: {names}", ""]
        group = [m for m in matches if m.family == family]
        if not group:
            sections += ["- **No tracks.**", ""]
            continue
        matched, review, unmatched = _by_status(group)
        sections.append(
            f"- Matched: {len(matched)}, needs review: {len(review)}, unmatched: {len(unmatched)}"
        )
        if review:
            best = [f"{m.title} (best {m.candidates[0].score.value:.2f})" for m in review]
            titles = ", ".join(best)
            sections.append(f"- Needs review: {titles}")
        if unmatched:
            sections.append(f"- Unmatched: {', '.join(m.title for m in unmatched)}")
        sections.append("")
    return "\n".join(sections)


def match_summary(games: list[Game], matches: list[Match]) -> str:
    lines = ["family  matched  review  unmatched"]
    for family in _families(games):
        matched, review, unmatched = _by_status([m for m in matches if m.family == family])
        lines.append(
            f"{family:<6}{len(matched):>5} matched{len(review):>5} review"
            f"{len(unmatched):>5} unmatched"
        )
    return "\n".join(lines)


def _by_status(matches: list[Match]) -> tuple[list[Match], list[Match], list[Match]]:
    return (
        [m for m in matches if m.status == "matched"],
        [m for m in matches if m.status == "review"],
        [m for m in matches if m.status == "unmatched"],
    )


def _families(games: list[Game]) -> dict[str, list[Game]]:
    families: dict[str, list[Game]] = {}
    for game in games:
        families.setdefault(game.family_id, []).append(game)
    return families


def _exclusive_groups(
    tracks: list[DraftTrack], family_order: list[str]
) -> list[tuple[str, list[DraftTrack]]]:
    """Exclusive tracks grouped by their game set, largest group first."""
    groups: dict[tuple[str, ...], list[DraftTrack]] = {}
    for track in tracks:
        if track.games is not None:
            groups.setdefault(tuple(track.games), []).append(track)
    ordered = sorted(
        groups.items(), key=lambda item: (-len(item[1]), [family_order.index(g) for g in item[0]])
    )
    return [("+".join(key), group) for key, group in ordered]


def _excluded_lines(excluded: list[Excluded]) -> list[str]:
    lines = []
    jingles = [e for e in excluded if e.reason == "jingle"]
    by_override = [e for e in excluded if e.reason == "override"]
    if jingles:
        titles = ", ".join(f"{e.title} {_minutes(e.duration_ms)}" for e in jingles)
        lines.append(f"- Excluded jingles: {len(jingles)} ({titles})")
    if by_override:
        titles = ", ".join(e.title for e in by_override)
        lines.append(f"- Excluded by override: {len(by_override)} ({titles})")
    return lines


def _minutes(duration_ms: int | None) -> str:
    if duration_ms is None:
        return "(no duration)"
    seconds = duration_ms // 1000
    return f"{seconds // 60}:{seconds % 60:02d}"
