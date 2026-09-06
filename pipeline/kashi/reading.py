"""일본어 가사 -> 읽기(가나) + 한글 발음.

UniDic 은 형태소마다 두 가지 읽기를 준다.

- ``kana``: 표기 그대로의 가나. 東京 -> トウキョウ
- ``pron``: 실제 발음. 東京 -> トーキョー, 조사 は -> ワ, へ -> エ

조사 は/へ/を 와 장음 처리가 사전 단계에서 이미 끝나 있으므로 ``pron`` 을 발음 모드의
입력으로 쓴다. 표기법 모드는 ``kana`` 를 쓴다.

한자의 특수 읽기(当て字)는 형태소 분석기가 잡지 못한다. 가사에서는 흔한 일이라
``overrides`` 로 직접 읽기를 지정할 수 있게 해 두었다.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from functools import lru_cache

from .hangul import Mode, to_hangul

# 앞 어절에 붙여 읽는 품사. 새 어절을 시작하지 않는다.
_CLITIC_POS = {"助詞", "助動詞", "接尾辞"}
_PUNCT_POS = {"補助記号", "空白"}


@dataclass
class Token:
    """형태소 하나."""

    surface: str
    kana: str  # 표기 그대로의 가나
    pron: str  # 실제 발음 가나
    pos: str
    lemma: str

    def source_kana(self, mode: Mode) -> str:
        if mode is not Mode.OFFICIAL:
            return self.pron
        # 표기법 모드는 표기 그대로의 가나를 쓰되(エイ -> 에이 를 지키려면 필요하다),
        # 조사만은 발음을 따른다. は -> ワ, へ -> エ, を -> オ.
        return self.pron if self.pos == "助詞" else self.kana


@dataclass
class Chunk:
    """자립어 + 뒤에 붙는 조사/조동사를 묶은 어절. 한글 표기의 띄어쓰기 단위."""

    tokens: list[Token] = field(default_factory=list)

    @property
    def surface(self) -> str:
        return "".join(t.surface for t in self.tokens)

    def kana(self, mode: Mode) -> str:
        return "".join(t.source_kana(mode) for t in self.tokens)

    def hangul(self, mode: Mode) -> str:
        # 어절 전체를 한 번에 변환한다. 어두 평음화는 첫 모라에만 걸리고, 형태소 경계를 넘는
        # 장음(行こ+う -> イコー)도 하나로 이어져야 제대로 접힌다.
        return to_hangul(self.kana(mode), mode, word_initial=True)


@dataclass
class AnalyzedLine:
    original: str
    chunks: list[Chunk]

    def reading(self, mode: Mode = Mode.PRONUNCIATION) -> str:
        return " ".join(c.kana(mode) for c in self.chunks if c.kana(mode).strip())

    def hangul(self, mode: Mode = Mode.PRONUNCIATION) -> str:
        parts = [c.hangul(mode) for c in self.chunks]
        return " ".join(p for p in parts if p.strip())

    def tokens(self) -> list[Token]:
        return [t for c in self.chunks for t in c.tokens]


@lru_cache(maxsize=1)
def _tagger():
    try:
        import fugashi
    except ImportError as exc:  # pragma: no cover - 설치 안내용
        raise RuntimeError(
            "fugashi 가 필요합니다. `pip install fugashi unidic-lite` 로 설치하세요."
        ) from exc

    # 전체 UniDic 이 읽기 정확도가 더 좋다. 없으면 unidic-lite 로 떨어진다.
    for module in ("unidic", "unidic_lite"):
        try:
            dic = __import__(module)
            return fugashi.Tagger(f"-d {dic.DICDIR}")
        except (ImportError, RuntimeError):
            continue
    raise RuntimeError(
        "UniDic 사전이 없습니다. `pip install unidic-lite` 또는 "
        "`pip install unidic && python -m unidic download` 를 실행하세요."
    )


def _feature(word, name: str) -> str:
    value = getattr(word.feature, name, None)
    # UniDic 은 값이 없을 때 '*' 또는 None 을 준다.
    return "" if value in (None, "*") else str(value)


def _to_token(word) -> Token:
    surface = word.surface
    kana = _feature(word, "kana") or surface
    pron = _feature(word, "pron") or kana
    return Token(
        surface=surface,
        kana=kana,
        pron=pron,
        pos=_feature(word, "pos1"),
        lemma=_feature(word, "lemma") or surface,
    )


def _split_by_overrides(text: str, overrides: dict[str, str]):
    """읽기를 직접 지정한 표현을 기준으로 줄을 잘라낸다.

    ``("override", 표현, 읽기)`` 와 ``("text", 조각)`` 을 순서대로 내보낸다.
    긴 표현을 먼저 맞춰야 짧은 표현에 먹히지 않는다.
    """
    if not overrides:
        yield ("text", text, "")
        return

    keys = sorted(overrides, key=len, reverse=True)
    buffer: list[str] = []
    i = 0
    while i < len(text):
        for key in keys:
            if text.startswith(key, i):
                if buffer:
                    yield ("text", "".join(buffer), "")
                    buffer = []
                yield ("override", key, overrides[key])
                i += len(key)
                break
        else:
            buffer.append(text[i])
            i += 1
    if buffer:
        yield ("text", "".join(buffer), "")


# お단으로 끝나는 가나. 의지형 う 가 이 뒤에 오면 장음이다.
_O_ROW = set("オコソトノホモヨロヲゴゾドボポョ")


def _normalize_volitional_u(tokens: list[Token]) -> None:
    """行こ+う 처럼 쪼개진 의지형의 조동사 う 를 장음(ー)으로 바꾼다.

    UniDic 은 대개 한 덩어리(イコー)로 주지만 IPADIC(안드로이드 쪽)은 쪼갠다.
    두 구현이 같은 결과를 내도록 양쪽에 같은 규칙을 둔다.
    """
    for previous, token in zip(tokens, tokens[1:]):
        if token.pos == "助動詞" and token.surface == "う" and previous.pron[-1:] in _O_ROW:
            token.pron = "ー"


def analyze(text: str, overrides: dict[str, str] | None = None) -> AnalyzedLine:
    """가사 한 줄을 형태소 분석해 어절 단위로 묶는다."""
    tagger = _tagger()
    tokens: list[Token] = []

    for kind, piece, reading in _split_by_overrides(text, overrides or {}):
        if kind == "override":
            tokens.append(
                Token(surface=piece, kana=reading, pron=reading, pos="名詞", lemma=piece)
            )
        else:
            tokens.extend(_to_token(word) for word in tagger(piece))

    _normalize_volitional_u(tokens)

    chunks: list[Chunk] = []
    for token in tokens:
        starts_new = (
            not chunks
            or token.pos not in _CLITIC_POS
            or chunks[-1].tokens[-1].pos in _PUNCT_POS
        )
        if starts_new:
            chunks.append(Chunk())
        chunks[-1].tokens.append(token)

    return AnalyzedLine(original=text, chunks=chunks)


def analyze_lines(
    lines: list[str], overrides: dict[str, str] | None = None
) -> list[AnalyzedLine]:
    return [analyze(line, overrides) for line in lines]
