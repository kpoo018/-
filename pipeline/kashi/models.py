"""안드로이드 앱이 받아가는 가사 문서 형식.

읽기(``reading``)를 한글 표기와 따로 담아 둔다. 표기 규칙을 손보더라도 형태소 분석을
다시 돌리지 않고 다시 렌더링할 수 있다.
"""

from __future__ import annotations

import hashlib
from dataclasses import asdict, dataclass, field
from typing import Any

SCHEMA_VERSION = 1


@dataclass
class TokenView:
    surface: str
    reading: str
    pos: str
    gloss: str = ""


@dataclass
class LyricLine:
    #: 곡 시작부터의 초. 싱크 정보가 없는 가사는 None.
    time: float | None
    original: str
    reading: str
    hangul: str
    translation: str = ""
    tokens: list[TokenView] = field(default_factory=list)


@dataclass
class LyricDoc:
    track_id: str
    artist: str
    title: str
    lines: list[LyricLine]
    album: str = ""
    duration: float = 0.0
    #: 한글 표기 모드. "pronunciation" 또는 "official".
    hangul_mode: str = "pronunciation"
    synced: bool = False
    source: str = ""
    translator: str = ""
    schema_version: int = SCHEMA_VERSION

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> LyricDoc:
        lines = [
            LyricLine(
                **{**line, "tokens": [TokenView(**t) for t in line.get("tokens", [])]}
            )
            for line in data.get("lines", [])
        ]
        return cls(**{**data, "lines": lines})


def make_track_id(artist: str, title: str, album: str = "") -> str:
    """제목/아티스트로부터 안정적인 캐시 키를 만든다."""
    raw = "".join(p.strip().casefold() for p in (artist, title, album))
    return hashlib.sha1(raw.encode("utf-8")).hexdigest()[:16]
