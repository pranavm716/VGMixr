"""The confidence that a YouTube video is a track's audio: a hand-tuned 0-1 score.

The score combines title similarity (after stripping noise such as "Pokémon", game names and
"OST"), evidence that the video is from the right game (a remake or another game is heavily
penalized), red flags (remix, cover, hour-long...), whether the length is plausible, how much the
source is trusted (a hand-picked playlist over a search) and the entry's position in its playlist.
The weights are tuned against the hand-labeled set in tests/data/youtube/labeled.json.
"""

import re
import unicodedata
from collections.abc import Iterable
from dataclasses import dataclass, field
from difflib import SequenceMatcher
from enum import Enum
from typing import Literal

from vgmixr_pipeline.manifest.models import Game

type Kind = Literal["playlist", "search", "album"]
type Evidence = Literal["right", "neutral", "wrong"]


@dataclass(frozen=True)
class Weights:
    title: float = 0.65
    evidence: float = 0.15
    trust: float = 0.10
    position: float = 0.10
    # Title similarity's share from characters; the rest is from shared words.
    characters: float = 0.7
    # The least title similarity of a title whose words (two or more) are all in the other's:
    # enough to accept when the video's title only leaves out descriptive words ("Mt. Moon" for
    # "Caves of Mt. Moon"), only close when it adds words ("Safari Zone Gate" for "Safari Zone").
    contained: float = 0.82
    contained_video: float = 0.87
    # Multipliers.
    wrong_game: float = 0.15
    red_flag: float = 0.5
    bad_duration: float = 0.4
    # A different set of digits ("Route 1" vs "Route 10") is a different track.
    other_numbers: float = 0.5
    trust_by_kind: dict[str, float] = field(
        default_factory=lambda: {"playlist": 1.0, "album": 0.9, "search": 0.8}
    )
    # Position fit falls from 1 to 0 as relative positions differ by 0 to 1/position_falloff.
    position_falloff: float = 4.0


WEIGHTS = Weights()
ACCEPT = 0.80
REVIEW = 0.50
# Longer than this, a whole video (not an album chapter) is a compilation or a loop.
LONG_MS = 20 * 60 * 1000
# A video can be shorter than the track's listed length by a little, or loop it a few times.
# The lower bound allows a one-loop rip of a track the album plays twice.
MIN_RATIO = 0.4
MAX_RATIO = 3.5


class Verdict(Enum):
    ACCEPT = "accept"
    REVIEW = "review"
    REJECT = "reject"


@dataclass(frozen=True)
class TrackFacts:
    """What scoring needs to know about a track."""

    family: str
    # The track's games, resolved (all of the family's when the track has no `games`).
    games: frozenset[str]
    title: str
    duration_ms: int | None
    # The track's relative position in its family, 0-1.
    position: float | None = None
    # Game Boy music is 8-bit, so "8-bit" is no red flag there.
    game_boy: bool = False


@dataclass(frozen=True)
class Candidate:
    """A video, or a slice of one (an album chapter), that might be a track."""

    video_id: str
    title: str
    duration_ms: int | None
    channel: str | None
    kind: Kind
    # The games of the playlist or album the candidate came from; empty for a search result.
    games: frozenset[str] = frozenset()
    # Relative position in its playlist, 0-1.
    position: float | None = None
    start_ms: int = 0
    end_ms: int | None = None
    # Words that say nothing about this candidate, such as those its whole playlist shares.
    noise: frozenset[str] = frozenset()


@dataclass(frozen=True)
class Score:
    value: float
    reasons: list[str]


# Text


_REPLACEMENTS = {"&": " and ", "♀": " female ", "♂": " male "}
_APOSTROPHES = re.compile(r"['’‘]")
_NON_WORD = re.compile(r"[^a-z0-9]+")
# "#12" anywhere; "12." / "12 -" / "12)" at the start; disc and track markers ("(D1;T4)",
# "Disc 1 Track 4").
_TRACK_NUMBER = re.compile(
    r"#\d+|^\s*\d{1,3}\s*[.):-]\s+|\(?\bD\d+\s*[;,.]?\s*T\d+\b\)?|\bdisc\s*\d+\s*track\s*\d+\b|^\s*0\d{1,2}\s+",
    re.IGNORECASE,
)
_NOISE = {
    "pokemon", "zelda", "legend", "ost", "soundtrack", "music", "bgm", "hq", "hd", "gba", "nds",
    "ds", "3ds", "gb", "gbc", "switch", "official", "audio", "game", "theme", "version", "versions",
    "ver", "vs", "and", "the", "of", "a", "1080p", "720p", "4k", "rip",
}  # fmt: skip
# Single words that change what a video is. Matched as whole words.
_FLAGS = {
    "remix", "remixed", "cover", "covers", "orchestra", "orchestral", "orchestrated",
    "reorchestrated", "arrange", "arranged", "arrangement", "piano", "guitar", "metal", "rock",
    "jazz", "lofi", "mashup", "extended", "hour", "hours", "loop", "looped", "slowed", "reverb",
    "nightcore", "remake", "remaster", "remastered", "soundfont", "fanmade", "fan", "beta",
    "unused", "acapella", "acoustic", "synthesia", "medley", "symphony", "concert", "live",
    # Videos about the game rather than its music.
    "how", "tutorial", "guide", "walkthrough", "playthrough", "gameplay", "speedrun", "nuzlocke",
    "reaction", "review", "glitch",
    # Other versions: fan transcriptions and MIDIs, edits, dubs.
    "transcription", "transcribed", "midi", "without", "dub", "dubbed", "spanish", "french",
    "german", "italian", "latino",
}  # fmt: skip
# Phrases that change what a video is.
_FLAG_PHRASES = [("music", "box"), ("8", "bit"), ("16", "bit")]
# Other consoles' sound: a demake, unless the track is from that console.
_DEMAKE = {"gbc", "nes", "snes", "n64", "genesis", "megadrive"}
# A cover names the original game: 'Song (From "Pokémon Ruby")'.
_FROM = re.compile(r"\(\s*from\s*[\"“]", re.IGNORECASE)
# Channels whose uploads aren't what their titles say (SiIvaGunner's "rips" are jokes).
_BAD_CHANNELS = {"siivagunner", "tsimfd"}
# Who a battle or encounter is against; a video title can't leave these out.
_ROLES = {
    "leader", "leaders", "gym", "elite", "champion", "rival", "wild", "trainer", "trainers",
    "legendary", "boss", "admin", "admins", "grunt", "grunts", "final", "frontier", "brain",
}  # fmt: skip
# Words that tell versions of one track apart.
_VARIANTS = {
    "kanto", "johto", "hoenn", "sinnoh", "unova", "kalos", "alola", "galar", "hisui", "paldea",
    "kitakami", "blueberry", "day", "night", "morning", "evening",
}  # fmt: skip
# Spin-offs, other media: never a mainline game's audio.
_OTHER_GAMES = [
    "mystery dungeon", "pokemon ranger", "colosseum", "pokemon xd", "pokemon stadium",
    "pokemon snap", "pokemon unite", "pokemon masters", "pokemon go", "puzzle league", "pinball",
    "trading card game", "tcg", "pokemon conquest", "pokemon rumble", "battle revolution",
    "anime", "super smash", "pokken", "cafe mix", "pokemon sleep", "pokemon shuffle",
    "pokemon channel", "pokemon dash",
]  # fmt: skip


def words(text: str) -> list[str]:
    """Lower-case words without accents or punctuation; `&` becomes "and", "Let's" "lets"."""
    for old, new in _REPLACEMENTS.items():
        text = text.replace(old, new)
    decomposed = unicodedata.normalize("NFKD", text)
    plain = "".join(c for c in decomposed if not unicodedata.combining(c))
    return _NON_WORD.sub(" ", _APOSTROPHES.sub("", plain.lower())).split()


def video_words(title: str) -> list[str]:
    return words(_TRACK_NUMBER.sub(" ", title))


# Game names


_COMPOUND = re.compile(r"([a-z])([A-Z])")
_ENGLISH = {"or", "as", "go"}


@dataclass(frozen=True)
class _Name:
    words: tuple[str, ...]
    family: str
    # Distinctive names count anywhere; plain words ("Gold", "Moon") only right after "Pokémon".
    anywhere: bool


@dataclass(frozen=True)
class _Found:
    families: frozenset[str]
    # Words with names, "Pokémon" and noise taken out.
    rest: list[str]
    # Spans of the words that were names, by first index.
    spans: tuple[tuple[str, ...], ...]


class Names:
    """Finds game names in video titles: game names, family labels and hand-written aliases."""

    def __init__(self, names: Iterable[_Name], family_games: dict[str, frozenset[str]]) -> None:
        # Longest first, so "Omega Ruby" is found before "Ruby".
        self._names = sorted(names, key=lambda n: -len(n.words))
        self._other = [tuple(words(o)) for o in _OTHER_GAMES]
        self.family_games = family_games

    @classmethod
    def build(cls, games: list[Game], aliases: dict[str, list[str]]) -> "Names":
        family_games: dict[str, list[Game]] = {}
        for game in games:
            family_games.setdefault(game.family_id, []).append(game)
        names = []
        for game in games:
            name = tuple(words(game.name))
            compound = bool(_COMPOUND.search(game.name))
            names.append(_Name(name, game.family_id, len(name) > 1 or compound))
            if compound:  # "HeartGold" is also written "Heart Gold"
                split = tuple(words(_COMPOUND.sub(r"\1 \2", game.name)))
                names.append(_Name(split, game.family_id, True))
        for family, members in family_games.items():
            if len(members) > 1:
                label = tuple(words("".join(g.shortName for g in members)))
                names.append(_Name(label, family, len(label[0]) >= 3))
                split = tuple(w for g in members for w in words(g.shortName))
                distinct = len(split) >= 3 or all(len(w) >= 2 and w not in _ENGLISH for w in split)
                names.append(_Name(split, family, distinct))
        game_family = {g.id: g.family_id for g in games}
        for key, values in aliases.items():
            family = game_family.get(key, key)
            names += [_Name(tuple(words(v)), family, True) for v in values]

        # Filter out _OTHER_GAMES entries if they match a catalog game or family alias
        name_words = {n.words for n in names}
        other = [tuple(words(o)) for o in _OTHER_GAMES if tuple(words(o)) not in name_words]

        inst = cls(names, {f: frozenset(g.id for g in m) for f, m in family_games.items()})
        inst._other = other
        return inst

    def find(self, title: str, ignore: tuple[tuple[str, ...], ...] = ()) -> _Found:
        """Names in `title`, except those in `ignore` (names that are part of a track's title)."""
        tokens = video_words(title)
        families: set[str] = set()
        spans: list[tuple[str, ...]] = []
        rest: list[str] = []
        i = 0
        while i < len(tokens):
            other = self._match_other(tokens, i)
            if other and other not in ignore:
                families.add("")  # another game entirely
                spans.append(other)
                rest += other
                i += len(other)
                continue
            if tokens[i] == "pokemon":
                i += 1
                while i < len(tokens):
                    name = self._match(tokens, i, chained=True)
                    if name is not None and name.words not in ignore:
                        families.add(name.family)
                        spans.append(name.words)
                        i += len(name.words)
                    elif tokens[i] in {"and", "version", "versions"}:
                        i += 1
                    else:
                        break
                continue
            name = self._match(tokens, i, chained=False)
            if name is not None and name.words not in ignore:
                families.add(name.family)
                spans.append(name.words)
                i += len(name.words)
                continue
            rest.append(tokens[i])
            i += 1
        return _Found(frozenset(families), [w for w in rest if w not in _NOISE], tuple(spans))

    def _match(self, tokens: list[str], i: int, chained: bool) -> _Name | None:
        for name in self._names:
            if (chained or name.anywhere) and tuple(tokens[i : i + len(name.words)]) == name.words:
                return name
        return None

    def _match_other(self, tokens: list[str], i: int) -> tuple[str, ...] | None:
        for other in self._other:
            if tuple(tokens[i : i + len(other)]) == other:
                return other
        return None

    def evidence(self, track: TrackFacts, candidate: Candidate) -> Evidence:
        if candidate.games & track.games:
            return "right"
        in_title = self.find(track.title).spans
        named = self.find(candidate.title, ignore=in_title).families
        if candidate.channel:
            channel = self.find(candidate.channel, ignore=in_title).families
            if track.family in channel:
                return "right"
        if track.family in named:
            return "right"
        return "wrong" if named else "neutral"


# Components


def title_similarity(
    track: TrackFacts, video_title: str, names: Names, noise: frozenset[str] = frozenset()
) -> float:
    """0-1. Names that are part of the track's title stay on both sides."""
    own = names.find(track.title)
    a = own.rest + [w for span in own.spans for w in span]
    # Ignored names stay in `rest`, so they're compared like any other word.
    b = [w for w in names.find(video_title, ignore=own.spans).rest if w not in noise]
    if not a or not b:
        return 0.0
    # Characters forgive small spelling differences; shared words keep unrelated titles apart.
    characters = max(
        SequenceMatcher(None, " ".join(a), " ".join(b)).ratio(),
        SequenceMatcher(None, " ".join(sorted(a)), " ".join(sorted(b))).ratio(),
    )
    shared = 2 * len(set(a) & set(b)) / (len(set(a)) + len(set(b)))
    ratio = WEIGHTS.characters * characters + (1 - WEIGHTS.characters) * shared
    shorter, longer = sorted([set(a), set(b)], key=len)
    if len(shorter) >= 2 and shorter < longer and len(shorter) > len(longer) / 2:
        left_out = set(a) - set(b)
        video_inside = shorter == set(b) and not left_out & _ROLES
        ratio = max(ratio, WEIGHTS.contained_video if video_inside else WEIGHTS.contained)
    if sorted(w for w in a if w.isdigit()) != sorted(w for w in b if w.isdigit()):
        ratio *= WEIGHTS.other_numbers
    # A variant only the video names (Johto for a Kanto track, Day for Night) is another track;
    # one only the track names may just be left out of the video's title.
    if (set(b) & _VARIANTS) - set(a):
        ratio *= WEIGHTS.other_numbers
    # Later games have Game Boy versions of some tracks ("Route 24 (GB Sounds)"), so "GB" on one
    # side only is another track.
    if not track.game_boy and ("gb" in words(track.title)) != ("gb" in video_words(video_title)):
        ratio *= WEIGHTS.other_numbers
    return ratio


def red_flags(
    track: TrackFacts, video_title: str, game_boy: bool = False, noise: frozenset[str] = frozenset()
) -> list[str]:
    own = set(words(track.title))
    tokens = [w for w in video_words(video_title) if w not in noise]
    flags = [w for w in dict.fromkeys(tokens) if w in _FLAGS and w not in own]
    game_boy = game_boy or track.game_boy
    for phrase in _FLAG_PHRASES:
        found = any(tuple(tokens[i : i + len(phrase)]) == phrase for i in range(len(tokens)))
        if found and not set(phrase) <= own and not (game_boy and phrase == ("8", "bit")):
            flags.append(" ".join(phrase))
    if not game_boy:
        flags += [w for w in dict.fromkeys(tokens) if w in _DEMAKE and w not in own]
    if _FROM.search(video_title):
        flags.append("from (a cover)")
    return flags


def duration_fits(expected_ms: int | None, actual_ms: int | None) -> bool:
    if expected_ms is None or actual_ms is None or expected_ms <= 0:
        return True
    return MIN_RATIO <= actual_ms / expected_ms <= MAX_RATIO


def _position_fit(track: TrackFacts, candidate: Candidate) -> float:
    if track.position is None or candidate.position is None:
        return 0.5
    distance = abs(track.position - candidate.position)
    return max(0.0, 1 - distance * WEIGHTS.position_falloff)


def score(track: TrackFacts, candidate: Candidate, names: Names) -> Score:
    w = WEIGHTS
    title = title_similarity(track, candidate.title, names, candidate.noise)
    evidence = names.evidence(track, candidate)
    trust = w.trust_by_kind[candidate.kind]
    position = _position_fit(track, candidate)
    # The title gates the rest: a right-game playlist entry with the wrong title is still wrong.
    value = title * (
        w.title
        + w.evidence * {"right": 1.0, "neutral": 0.5, "wrong": 0.0}[evidence]
        + w.trust * trust
        + w.position * position
    )
    reasons = [f"title {title:.2f}", f"{evidence} game", candidate.kind]
    if evidence == "wrong":
        value *= w.wrong_game
        reasons[1] = "wrong game"
    flags = red_flags(track, candidate.title, noise=candidate.noise)
    if candidate.channel and "".join(words(candidate.channel)) in _BAD_CHANNELS:
        flags.append(f"channel {candidate.channel}")
    if candidate.kind != "album" and (candidate.duration_ms or 0) > LONG_MS:
        flags.append("long video")
    for flag in flags:
        value *= w.red_flag
        reasons.append(f"red flag: {flag}")
    if not duration_fits(track.duration_ms, candidate.duration_ms):
        value *= w.bad_duration
        reasons.append(f"length {candidate.duration_ms or 0} ms vs {track.duration_ms} ms")
    return Score(min(1.0, max(0.0, value)), reasons)


def classify(value: float) -> Verdict:
    if value >= ACCEPT:
        return Verdict.ACCEPT
    if value >= REVIEW:
        return Verdict.REVIEW
    return Verdict.REJECT
