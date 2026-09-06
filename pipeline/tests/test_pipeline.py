import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from kashi import lrclib, pipeline  # noqa: E402
from kashi.cache import Cache  # noqa: E402
from kashi.hangul import Mode  # noqa: E402
from kashi.models import LyricDoc  # noqa: E402
from kashi.translate import Translator  # noqa: E402

SYNCED = """[ar:テスト]
[00:12.34]君はまだ知らない
[00:15.00]
[00:16.50]東京へ行こう
[00:20.10]walking in the rain
"""


def test_parse_lrc_reads_timestamps_and_drops_metadata():
    lines = lrclib.parse_lrc(SYNCED)
    assert [round(t, 2) for t, _ in lines] == [12.34, 15.0, 16.5, 20.1]
    assert lines[0][1] == "君はまだ知らない"
    assert lines[1][1] == ""  # 간주 줄은 남긴다


def test_parse_lrc_expands_repeated_timestamps_and_sorts():
    lines = lrclib.parse_lrc("[00:05.00][01:05.00]ラララ\n[00:30.00]あ")
    assert [round(t, 2) for t, _ in lines] == [5.0, 30.0, 65.0]
    assert lines[0][1] == "ラララ" and lines[2][1] == "ラララ"


def test_parse_lrc_without_timestamps():
    lines = lrclib.parse_lrc("君はまだ知らない\n東京へ行こう")
    assert lines == [(None, "君はまだ知らない"), (None, "東京へ行こう")]


@pytest.fixture
def stub_track(monkeypatch):
    track = lrclib.LrclibTrack(
        id=42,
        artist="テスト",
        title="サンプル",
        album="アルバム",
        duration=200.0,
        plain_lyrics="",
        synced_lyrics=SYNCED,
    )
    monkeypatch.setattr(lrclib, "find", lambda *a, **k: track)
    monkeypatch.setattr(pipeline.lrclib, "find", lambda *a, **k: track)
    return track


class StubTranslator(Translator):
    name = "stub"

    def translate_lines(self, lines):
        return ["[번역] " + line if line.strip() else "" for line in lines]


def test_build_produces_three_layer_lines(stub_track):
    doc = pipeline.build(artist="テスト", title="サンプル", translator=StubTranslator())

    assert doc.synced is True
    assert doc.source == "lrclib:42"
    assert doc.translator == "stub"
    assert len(doc.lines) == 4

    first = doc.lines[0]
    assert first.time == pytest.approx(12.34)
    assert first.original == "君はまだ知らない"
    assert first.hangul == "키미와 마다 시라나이"
    assert first.translation == "[번역] 君はまだ知らない"

    # 간주 줄은 원문도 발음도 뜻도 비어 있어야 한다
    interlude = doc.lines[1]
    assert interlude.original == "" and interlude.hangul == "" and interlude.translation == ""

    # 영어 가사는 원문 그대로 흘려보낸다
    assert "walking in the rain" in doc.lines[3].hangul


def test_build_official_mode_differs(stub_track):
    doc = pipeline.build(artist="テスト", title="サンプル", mode=Mode.OFFICIAL)
    assert doc.hangul_mode == "official"
    assert doc.lines[2].hangul == "도쿄에 이코"


def test_build_without_translator_leaves_translation_empty(stub_track):
    doc = pipeline.build(artist="テスト", title="サンプル")
    assert all(line.translation == "" for line in doc.lines)
    assert doc.translator == "none"


def test_build_with_tokens(stub_track):
    doc = pipeline.build(artist="テスト", title="サンプル", with_tokens=True)
    surfaces = [t.surface for t in doc.lines[0].tokens]
    assert surfaces == ["君", "は", "まだ", "知ら", "ない"]
    assert doc.lines[0].tokens[1].pos == "助詞"


def test_build_raises_when_not_found(monkeypatch):
    monkeypatch.setattr(pipeline.lrclib, "find", lambda *a, **k: None)
    with pytest.raises(pipeline.LyricsNotFound):
        pipeline.build(artist="없음", title="없음")


def test_cache_round_trip(tmp_path, stub_track):
    cache = Cache(tmp_path)
    doc = pipeline.build(artist="テスト", title="サンプル", translator=StubTranslator())
    cache.save(doc)

    loaded = cache.load(doc.track_id, doc.hangul_mode)
    assert isinstance(loaded, LyricDoc)
    assert loaded.to_dict() == doc.to_dict()


def test_cache_miss_and_corrupt_file(tmp_path):
    cache = Cache(tmp_path)
    assert cache.load("nope", "pronunciation") is None

    path = cache.path("broken", "pronunciation")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("{ not json", "utf-8")
    assert cache.load("broken", "pronunciation") is None


def test_cache_separates_modes(tmp_path, stub_track):
    cache = Cache(tmp_path)
    for mode in (Mode.PRONUNCIATION, Mode.OFFICIAL):
        cache.save(pipeline.build(artist="テスト", title="サンプル", mode=mode))

    track_id = pipeline.build(artist="テスト", title="サンプル").track_id
    assert cache.load(track_id, "pronunciation").lines[2].hangul == "토오쿄오에 이코오"
    assert cache.load(track_id, "official").lines[2].hangul == "도쿄에 이코"
