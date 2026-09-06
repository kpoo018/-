"""LRCLIB 클라이언트와 LRC 파서.

LRCLIB 은 API 키가 없고, 평문 가사와 시간이 붙은 가사를 함께 준다.
https://lrclib.net/docs
"""

from __future__ import annotations

import json
import re
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass

BASE_URL = "https://lrclib.net/api"
# LRCLIB 은 어떤 앱이 부르는지 알 수 있는 User-Agent 를 요청한다.
USER_AGENT = "kashi/0.1 (https://github.com/kpoo018)"
TIMEOUT = 15

_LRC_TIMESTAMP = re.compile(r"\[(\d+):(\d{1,2})(?:[.:](\d{1,3}))?\]")


class LrclibError(RuntimeError):
    pass


@dataclass
class LrclibTrack:
    id: int
    artist: str
    title: str
    album: str
    duration: float
    plain_lyrics: str
    synced_lyrics: str

    @property
    def is_instrumental(self) -> bool:
        return not (self.plain_lyrics or self.synced_lyrics)

    @classmethod
    def from_json(cls, data: dict) -> LrclibTrack:
        return cls(
            id=data.get("id", 0),
            artist=data.get("artistName") or "",
            title=data.get("trackName") or "",
            album=data.get("albumName") or "",
            duration=float(data.get("duration") or 0.0),
            plain_lyrics=data.get("plainLyrics") or "",
            synced_lyrics=data.get("syncedLyrics") or "",
        )


def _request(path: str, params: dict[str, str]) -> object:
    query = urllib.parse.urlencode({k: v for k, v in params.items() if v})
    url = f"{BASE_URL}/{path}?{query}"
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            return None
        raise LrclibError(f"LRCLIB {exc.code}: {exc.reason}") from exc
    except urllib.error.URLError as exc:
        raise LrclibError(f"LRCLIB 에 연결하지 못했습니다: {exc.reason}") from exc


def get(
    artist: str, title: str, album: str = "", duration: float | None = None
) -> LrclibTrack | None:
    """정확 매칭. 재생 길이를 함께 주면 다른 버전을 집을 확률이 크게 줄어든다."""
    params = {"artist_name": artist, "track_name": title, "album_name": album}
    if duration:
        params["duration"] = str(int(round(duration)))
    data = _request("get", params)
    return LrclibTrack.from_json(data) if isinstance(data, dict) else None


def search(query: str = "", artist: str = "", title: str = "") -> list[LrclibTrack]:
    params = {"q": query, "artist_name": artist, "track_name": title}
    data = _request("search", params)
    return [LrclibTrack.from_json(item) for item in data] if isinstance(data, list) else []


def find(
    artist: str, title: str, album: str = "", duration: float | None = None
) -> LrclibTrack | None:
    """정확 매칭을 먼저 보고, 없으면 검색으로 떨어진다.

    검색 결과 중에서는 시간이 붙은 가사를, 그 다음으로 재생 길이가 가까운 것을 고른다.
    """
    track = get(artist, title, album, duration)
    if track and not track.is_instrumental:
        return track

    candidates = [t for t in search(artist=artist, title=title) if not t.is_instrumental]
    if not candidates and (artist or title):
        candidates = [
            t for t in search(query=f"{artist} {title}".strip()) if not t.is_instrumental
        ]
    if not candidates:
        return None

    def rank(candidate: LrclibTrack) -> tuple[int, float]:
        gap = abs(candidate.duration - duration) if duration else 0.0
        return (0 if candidate.synced_lyrics else 1, gap)

    return min(candidates, key=rank)


def parse_lrc(text: str) -> list[tuple[float | None, str]]:
    """LRC 본문을 (초, 가사) 목록으로 바꾼다.

    한 줄에 타임스탬프가 여러 개 붙을 수 있다(반복 후렴). 결과는 시간순으로 정렬한다.
    """
    lines: list[tuple[float | None, str]] = []
    for raw in text.splitlines():
        stamps = list(_LRC_TIMESTAMP.finditer(raw))
        content = _LRC_TIMESTAMP.sub("", raw).strip()
        if not stamps:
            # [ar:...] 같은 메타데이터 줄은 버린다.
            if content and not re.match(r"^\[[a-z]+:", raw.strip()):
                lines.append((None, content))
            continue
        for stamp in stamps:
            minutes, seconds, fraction = stamp.groups()
            millis = int((fraction or "0").ljust(3, "0"))
            lines.append((int(minutes) * 60 + int(seconds) + millis / 1000, content))

    if any(time is not None for time, _ in lines):
        lines.sort(key=lambda item: item[0] if item[0] is not None else 0.0)
    return lines


def to_lines(track: LrclibTrack) -> tuple[list[tuple[float | None, str]], bool]:
    """가사 본문에서 (줄 목록, 싱크 여부)를 뽑는다. 빈 줄(간주)은 남긴다."""
    if track.synced_lyrics:
        return parse_lrc(track.synced_lyrics), True
    return [(None, line.strip()) for line in track.plain_lyrics.splitlines()], False
