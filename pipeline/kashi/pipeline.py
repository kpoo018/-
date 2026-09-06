"""가사 한 곡을 원문 + 한글 발음 + 뜻으로 가공한다."""

from __future__ import annotations

from . import lrclib
from .hangul import Mode
from .models import LyricDoc, LyricLine, TokenView, make_track_id
from .reading import analyze
from .translate import Translator


class LyricsNotFound(RuntimeError):
    pass


def build(
    artist: str,
    title: str,
    album: str = "",
    duration: float | None = None,
    mode: Mode = Mode.PRONUNCIATION,
    translator: Translator | None = None,
    overrides: dict[str, str] | None = None,
    with_tokens: bool = False,
) -> LyricDoc:
    """LRCLIB 에서 가사를 받아 3단(원문/발음/뜻) 문서로 만든다.

    ``with_tokens`` 를 켜면 단어별 읽기까지 담는다. 문서가 서너 배로 커지므로
    단어 단위 표시가 필요할 때만 쓴다.
    """
    track = lrclib.find(artist, title, album, duration)
    if track is None:
        raise LyricsNotFound(f"가사를 찾지 못했습니다: {artist} - {title}")

    raw_lines, synced = lrclib.to_lines(track)
    if not raw_lines:
        raise LyricsNotFound(f"가사가 비어 있습니다: {artist} - {title}")

    analyzed = [analyze(text, overrides) if text.strip() else None for _, text in raw_lines]

    translations = [""] * len(raw_lines)
    if translator is not None and translator.name != "none":
        translations = translator.translate_lines([text for _, text in raw_lines])

    lines = []
    for (time, text), parsed, translation in zip(raw_lines, analyzed, translations):
        tokens = []
        if with_tokens and parsed is not None:
            tokens = [
                TokenView(
                    surface=token.surface,
                    reading=token.source_kana(mode),
                    pos=token.pos,
                )
                for token in parsed.tokens()
            ]
        lines.append(
            LyricLine(
                time=time,
                original=text,
                reading=parsed.reading(mode) if parsed else "",
                hangul=parsed.hangul(mode) if parsed else "",
                translation=translation,
                tokens=tokens,
            )
        )

    return LyricDoc(
        track_id=make_track_id(track.artist, track.title, track.album),
        artist=track.artist,
        title=track.title,
        album=track.album,
        duration=track.duration,
        lines=lines,
        hangul_mode=mode.value,
        synced=synced,
        source=f"lrclib:{track.id}",
        translator=translator.name if translator else "none",
    )
