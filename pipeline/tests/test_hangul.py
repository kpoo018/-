import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from kashi.hangul import Mode, to_hangul  # noqa: E402


@pytest.mark.parametrize(
    "kana,expected",
    [
        # 기본 오십음
        ("シラナイ", "시라나이"),
        ("アイシテル", "아이시테루"),
        ("ハジメマシテ", "하지메마시테"),
        # 장음은 모음 반복으로 살린다
        ("トーキョー", "토오쿄오"),
        ("キョー", "쿄오"),
        ("ホントーニ", "혼토오니"),
        # 촉음은 뒤 자음에 맞춰 받침이 달라진다
        ("ガッコー", "각코오"),
        ("イッパイ", "입파이"),
        ("マッテ", "맛테"),
        # 발음(ん)도 뒤 자음에 동화된다
        ("サンポ", "삼포"),
        ("センパイ", "셈파이"),
        ("リンゴ", "링고"),
        ("コンニチワ", "콘니치와"),
        # 요음
        ("ジャナイ", "자나이"),
        ("チョット", "촛토"),
        ("ツヨイ", "츠요이"),
        # 외래어 확장 가나
        ("ファイト", "파이토"),
        ("ジェット", "젯토"),
        ("ラヴ", "라부"),
    ],
)
def test_pronunciation_mode(kana, expected):
    assert to_hangul(kana) == expected


@pytest.mark.parametrize(
    "kana,expected",
    [
        # 어두 평음화 + 장음 생략
        ("トーキョー", "도쿄"),
        ("キョー", "교"),
        ("キミワ", "기미와"),
        ("コンニチワ", "곤니치와"),
        # 촉음은 언제나 ㅅ, 발음(ん)은 언제나 ㄴ
        ("ガッコー", "갓코"),
        ("イッパイ", "잇파이"),
        ("サンポ", "산포"),
        ("リンゴ", "린고"),
        # ツ 는 어디서든 '쓰'
        ("ツヨイ", "쓰요이"),
        ("ミツケタ", "미쓰케타"),
        # オウ/オオ/ウウ 는 장음이라 적지 않는다
        ("トウキョウ", "도쿄"),
        ("オオサカ", "오사카"),
        ("ユウキ", "유키"),
        # エイ 는 장음으로 치지 않는다
        ("センセイ", "센세이"),
        ("ヘイセイ", "헤이세이"),
    ],
)
def test_official_mode(kana, expected):
    assert to_hangul(kana, Mode.OFFICIAL) == expected


def test_hiragana_is_normalised_to_katakana():
    assert to_hangul("きみは") == to_hangul("キミハ")


def test_word_initial_flag_only_affects_official_mode():
    # 표기법 모드에서만 어두 평음화가 걸린다
    assert to_hangul("カ", Mode.OFFICIAL, word_initial=True) == "가"
    assert to_hangul("カ", Mode.OFFICIAL, word_initial=False) == "카"
    assert to_hangul("カ", Mode.PRONUNCIATION, word_initial=True) == "카"


def test_non_kana_passes_through():
    assert to_hangul("ドライブ (Drive)") == "도라이부 (Drive)"
    assert to_hangul("アイ、ラブ") == "아이、라부"


def test_standalone_moraic_nasal_after_closed_syllable():
    # 받침이 이미 찬 음절 뒤의 ん 은 홀로 선 음절로 적는다
    assert to_hangul("ンン") == "은은"


def test_empty_input():
    assert to_hangul("") == ""
