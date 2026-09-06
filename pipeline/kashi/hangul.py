"""가나 -> 한글 발음 표기 변환.

두 가지 모드를 제공한다.

- ``PRONUNCIATION`` (기본): 실제로 들리는 소리에 가깝게 적는다. 장음을 모음 반복으로
  살리고, か/た행을 어디서나 격음(카/타)으로 적는다. 따라 부르는 것이 목적일 때 쓴다.
- ``OFFICIAL``: 국립국어원 외래어 표기법(일본어)을 따른다. 장음을 적지 않고,
  か/た행이 어두에서 평음(가/다)이 된다. 고유명사·제목을 통일된 표기로 적을 때 쓴다.

입력은 히라가나/가타카나 모두 받는다. 가나가 아닌 문자는 그대로 통과시킨다.
"""

from __future__ import annotations

from enum import Enum

CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
JONG = "_ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"


class Mode(str, Enum):
    PRONUNCIATION = "pronunciation"
    OFFICIAL = "official"


def compose(cho: str, jung: str, jong: str = "") -> str:
    """초성/중성/종성 자모를 완성형 한글 한 글자로 조합한다."""
    return chr(
        0xAC00
        + (CHO.index(cho) * 21 + JUNG.index(jung)) * 28
        + (JONG.index(jong) if jong else 0)
    )


# 가나 한 모라 -> (초성, 중성). 발음 모드 기준값이다.
MORA: dict[str, tuple[str, str]] = {}


def _row(mapping: dict[str, str], cho: str) -> None:
    for kana, jung in mapping.items():
        MORA[kana] = (cho, jung)


_row({"ア": "ㅏ", "イ": "ㅣ", "ウ": "ㅜ", "エ": "ㅔ", "オ": "ㅗ"}, "ㅇ")
_row({"カ": "ㅏ", "キ": "ㅣ", "ク": "ㅜ", "ケ": "ㅔ", "コ": "ㅗ"}, "ㅋ")
_row({"ガ": "ㅏ", "ギ": "ㅣ", "グ": "ㅜ", "ゲ": "ㅔ", "ゴ": "ㅗ"}, "ㄱ")
_row({"サ": "ㅏ", "シ": "ㅣ", "ス": "ㅡ", "セ": "ㅔ", "ソ": "ㅗ"}, "ㅅ")
_row({"ザ": "ㅏ", "ジ": "ㅣ", "ズ": "ㅡ", "ゼ": "ㅔ", "ゾ": "ㅗ"}, "ㅈ")
_row({"タ": "ㅏ", "テ": "ㅔ", "ト": "ㅗ"}, "ㅌ")
_row({"チ": "ㅣ", "ツ": "ㅡ"}, "ㅊ")
_row({"ダ": "ㅏ", "デ": "ㅔ", "ド": "ㅗ"}, "ㄷ")
_row({"ヂ": "ㅣ", "ヅ": "ㅡ"}, "ㅈ")
_row({"ナ": "ㅏ", "ニ": "ㅣ", "ヌ": "ㅜ", "ネ": "ㅔ", "ノ": "ㅗ"}, "ㄴ")
_row({"ハ": "ㅏ", "ヒ": "ㅣ", "フ": "ㅜ", "ヘ": "ㅔ", "ホ": "ㅗ"}, "ㅎ")
_row({"バ": "ㅏ", "ビ": "ㅣ", "ブ": "ㅜ", "ベ": "ㅔ", "ボ": "ㅗ"}, "ㅂ")
_row({"パ": "ㅏ", "ピ": "ㅣ", "プ": "ㅜ", "ペ": "ㅔ", "ポ": "ㅗ"}, "ㅍ")
_row({"マ": "ㅏ", "ミ": "ㅣ", "ム": "ㅜ", "メ": "ㅔ", "モ": "ㅗ"}, "ㅁ")
_row({"ヤ": "ㅑ", "ユ": "ㅠ", "ヨ": "ㅛ"}, "ㅇ")
_row({"ラ": "ㅏ", "リ": "ㅣ", "ル": "ㅜ", "レ": "ㅔ", "ロ": "ㅗ"}, "ㄹ")
_row({"ワ": "ㅘ", "ヲ": "ㅗ", "ヰ": "ㅣ", "ヱ": "ㅔ"}, "ㅇ")
_row({"ヴ": "ㅜ"}, "ㅂ")

# 요음. ャ/ュ/ョ 는 초성을 유지한 채 중성만 바꾼다.
_row({"キャ": "ㅑ", "キュ": "ㅠ", "キョ": "ㅛ"}, "ㅋ")
_row({"ギャ": "ㅑ", "ギュ": "ㅠ", "ギョ": "ㅛ"}, "ㄱ")
_row({"シャ": "ㅑ", "シュ": "ㅠ", "ショ": "ㅛ"}, "ㅅ")
_row({"ジャ": "ㅏ", "ジュ": "ㅜ", "ジョ": "ㅗ"}, "ㅈ")  # 자/주/조 (쟈/쥬/죠 아님)
_row({"チャ": "ㅏ", "チュ": "ㅜ", "チョ": "ㅗ"}, "ㅊ")
_row({"ヂャ": "ㅏ", "ヂュ": "ㅜ", "ヂョ": "ㅗ"}, "ㅈ")
_row({"ニャ": "ㅑ", "ニュ": "ㅠ", "ニョ": "ㅛ"}, "ㄴ")
_row({"ヒャ": "ㅑ", "ヒュ": "ㅠ", "ヒョ": "ㅛ"}, "ㅎ")
_row({"ビャ": "ㅑ", "ビュ": "ㅠ", "ビョ": "ㅛ"}, "ㅂ")
_row({"ピャ": "ㅑ", "ピュ": "ㅠ", "ピョ": "ㅛ"}, "ㅍ")
_row({"ミャ": "ㅑ", "ミュ": "ㅠ", "ミョ": "ㅛ"}, "ㅁ")
_row({"リャ": "ㅑ", "リュ": "ㅠ", "リョ": "ㅛ"}, "ㄹ")

# 외래어 표기용 확장 가나. 가사에 영어 단어가 자주 섞여 들어온다.
_row({"ファ": "ㅏ", "フィ": "ㅣ", "フェ": "ㅔ", "フォ": "ㅗ", "フュ": "ㅠ"}, "ㅍ")
_row({"ヴァ": "ㅏ", "ヴィ": "ㅣ", "ヴェ": "ㅔ", "ヴォ": "ㅗ"}, "ㅂ")
_row({"ティ": "ㅣ", "トゥ": "ㅜ"}, "ㅌ")
_row({"ディ": "ㅣ", "ドゥ": "ㅜ"}, "ㄷ")
_row({"ウィ": "ㅟ", "ウェ": "ㅞ", "ウォ": "ㅝ"}, "ㅇ")
_row({"シェ": "ㅖ"}, "ㅅ")
_row({"ジェ": "ㅔ"}, "ㅈ")
_row({"チェ": "ㅔ"}, "ㅊ")
_row({"ツァ": "ㅏ", "ツィ": "ㅣ", "ツェ": "ㅔ", "ツォ": "ㅗ"}, "ㅊ")
_row({"クァ": "ㅘ", "クォ": "ㅝ"}, "ㅋ")
_row({"グァ": "ㅘ"}, "ㄱ")

# 어두에서 평음이 되는 모라 (표기법 모드에서만 쓴다).
OFFICIAL_INITIAL: dict[str, tuple[str, str]] = {
    "カ": ("ㄱ", "ㅏ"), "キ": ("ㄱ", "ㅣ"), "ク": ("ㄱ", "ㅜ"),
    "ケ": ("ㄱ", "ㅔ"), "コ": ("ㄱ", "ㅗ"),
    "キャ": ("ㄱ", "ㅑ"), "キュ": ("ㄱ", "ㅠ"), "キョ": ("ㄱ", "ㅛ"),
    "タ": ("ㄷ", "ㅏ"), "テ": ("ㄷ", "ㅔ"), "ト": ("ㄷ", "ㅗ"),
    "チ": ("ㅈ", "ㅣ"),
    "チャ": ("ㅈ", "ㅏ"), "チュ": ("ㅈ", "ㅜ"), "チョ": ("ㅈ", "ㅗ"),
}

# 표기법 모드는 어디서든 ツ를 '쓰'로 적는다.
OFFICIAL_ANYWHERE: dict[str, tuple[str, str]] = {"ツ": ("ㅆ", "ㅡ")}

# 장음을 적을 때 늘어나는 모음. 요음과 이중모음은 단모음으로 떨어뜨린다.
ELONG: dict[str, str] = {
    "ㅏ": "ㅏ", "ㅑ": "ㅏ", "ㅘ": "ㅏ",
    "ㅣ": "ㅣ", "ㅟ": "ㅣ",
    "ㅜ": "ㅜ", "ㅠ": "ㅜ",
    "ㅔ": "ㅔ", "ㅖ": "ㅔ", "ㅞ": "ㅔ",
    "ㅗ": "ㅗ", "ㅛ": "ㅗ", "ㅝ": "ㅗ",
    "ㅡ": "ㅡ",
}

SOKUON = "ッ"
HATSUON = "ン"
CHOON = "ー"
_SMALL_VOWELS = "ァィゥェォ"

# 표기법 모드에서 장음으로 보고 적지 않는 조합. {뒤따르는 모음 가나: 앞 음절의 모음}
# オウ/オオ -> 오, ウウ -> 우 는 장음이지만 エイ -> 에이 는 장음으로 치지 않는다.
_OFFICIAL_LONG_VOWEL: dict[str, set[str]] = {
    "ウ": {"ㅗ", "ㅜ"},
    "オ": {"ㅗ"},
    "ア": {"ㅏ"},
    "エ": {"ㅔ"},
    "イ": {"ㅣ"},
}

# 촉음 뒤에 오는 초성별 받침. 표에 없으면 ㅅ.
_SOKUON_JONG = {"ㅋ": "ㄱ", "ㄱ": "ㄱ", "ㅍ": "ㅂ", "ㅂ": "ㅂ"}
# 발음(ん) 뒤에 오는 초성별 받침. 표에 없으면 ㄴ.
_HATSUON_JONG = {"ㅁ": "ㅁ", "ㅂ": "ㅁ", "ㅍ": "ㅁ", "ㅋ": "ㅇ", "ㄱ": "ㅇ"}

_MAX_MORA_LEN = max(len(k) for k in MORA)


def to_katakana(text: str) -> str:
    """히라가나를 가타카나로 정규화한다. 다른 문자는 건드리지 않는다."""
    out = []
    for ch in text:
        code = ord(ch)
        out.append(chr(code + 0x60) if 0x3041 <= code <= 0x3096 else ch)
    return "".join(out)


def _is_long_vowel_tail(kana: str, units: list[tuple[str, object]]) -> bool:
    """이 모라가 바로 앞 음절을 늘이기만 하는 장음인지 본다 (표기법 모드 전용)."""
    allowed = _OFFICIAL_LONG_VOWEL.get(kana)
    if not allowed or not units or units[-1][0] != "mora":
        return False
    previous_jung = units[-1][1][1]  # type: ignore[index]
    return ELONG.get(previous_jung, previous_jung) in allowed


def _lookup(kana: str, mode: Mode, word_initial: bool) -> tuple[str, str] | None:
    if mode is Mode.OFFICIAL:
        if word_initial and kana in OFFICIAL_INITIAL:
            return OFFICIAL_INITIAL[kana]
        if kana in OFFICIAL_ANYWHERE:
            return OFFICIAL_ANYWHERE[kana]
    return MORA.get(kana)


def to_hangul(
    kana: str,
    mode: Mode = Mode.PRONUNCIATION,
    word_initial: bool = True,
) -> str:
    """가나 문자열을 한글 발음 표기로 바꾼다.

    ``word_initial`` 은 표기법 모드에서 어두 평음화를 적용할지 결정한다. 한 단어를
    여러 조각으로 나눠 넘길 때는 두 번째 조각부터 ``False`` 로 준다.
    """
    text = to_katakana(kana)

    # 1단계: 모라 단위로 쪼갠다. 긴 것부터 맞춰야 요음/확장 가나가 잡힌다.
    units: list[tuple[str, object]] = []
    i = 0
    at_word_start = word_initial
    while i < len(text):
        ch = text[i]
        if ch in (SOKUON, HATSUON, CHOON):
            units.append(
                ({SOKUON: "sokuon", HATSUON: "hatsuon", CHOON: "choon"}[ch], None)
            )
            i += 1
            continue

        for length in range(_MAX_MORA_LEN, 0, -1):
            chunk = text[i : i + length]
            found = _lookup(chunk, mode, at_word_start)
            if found:
                if mode is Mode.OFFICIAL and _is_long_vowel_tail(chunk, units):
                    i += length  # 표기법은 장음을 적지 않는다
                    break
                units.append(("mora", found))
                i += length
                at_word_start = False
                break
        else:
            # 매칭 실패. 남은 작은 가나는 버리고(직전 모라에 이미 반영됨),
            # 그 외 문자는 원문 그대로 흘려보낸다.
            if ch not in _SMALL_VOWELS:
                units.append(("raw", ch))
                at_word_start = True
            i += 1

    # 2단계: 받침을 붙여가며 음절을 조립한다.
    syllables: list[list[str]] = []  # [초성, 중성, 종성]
    out: list[str] = []

    def flush() -> None:
        for cho, jung, jong in syllables:
            out.append(compose(cho, jung, jong))
        syllables.clear()

    def next_cho(index: int) -> str | None:
        for kind, payload in units[index + 1 :]:
            if kind == "mora":
                return payload[0]  # type: ignore[index]
            if kind == "raw":
                return None
        return None

    for index, (kind, payload) in enumerate(units):
        if kind == "mora":
            cho, jung = payload  # type: ignore[misc]
            syllables.append([cho, jung, ""])
        elif kind == "choon":
            if mode is Mode.OFFICIAL:
                continue  # 표기법은 장음을 적지 않는다
            if syllables:
                syllables.append(["ㅇ", ELONG.get(syllables[-1][1], syllables[-1][1]), ""])
        elif kind in ("sokuon", "hatsuon"):
            following = next_cho(index) or ""
            if kind == "sokuon":
                jong = "ㅅ" if mode is Mode.OFFICIAL else _SOKUON_JONG.get(following, "ㅅ")
            else:
                jong = "ㄴ" if mode is Mode.OFFICIAL else _HATSUON_JONG.get(following, "ㄴ")
            if syllables and not syllables[-1][2]:
                syllables[-1][2] = jong
            else:
                # 직전 음절에 이미 받침이 있으면 홀로 선 음절로 적는다 (예: ん -> 은).
                syllables.append(["ㅇ", "ㅡ", jong])
        else:  # raw
            flush()
            out.append(payload)  # type: ignore[arg-type]

    flush()
    return "".join(out)
