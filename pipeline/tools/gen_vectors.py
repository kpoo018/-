"""한글 변환 규칙의 공유 테스트 벡터를 만든다.

Python 구현이 정답지다. Kotlin 구현은 이 파일과 똑같이 나와야 한다.
규칙을 바꾸면 이 스크립트를 다시 돌려 벡터를 갱신하고, 두 테스트가 모두 통과하는지 본다.

    python -m tools.gen_vectors > ../shared/hangul_vectors.json
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from kashi.hangul import Mode, to_hangul  # noqa: E402

INPUTS = [
    # 기본 오십음
    "アイウエオ", "カキクケコ", "サシスセソ", "タチツテト", "ナニヌネノ",
    "ハヒフヘホ", "マミムメモ", "ヤユヨ", "ラリルレロ", "ワヲン",
    "ガギグゲゴ", "ザジズゼゾ", "ダヂヅデド", "バビブベボ", "パピプペポ",
    # 요음
    "キャキュキョ", "ギャギュギョ", "シャシュショ", "ジャジュジョ", "チャチュチョ",
    "ニャニュニョ", "ヒャヒュヒョ", "ビャビュビョ", "ピャピュピョ", "ミャミュミョ", "リャリュリョ",
    # 외래어 가나
    "ファフィフェフォ", "ヴァヴィヴヴェヴォ", "ティトゥディドゥ", "ウィウェウォ",
    "シェジェチェ", "ツァツィツェツォ", "クァクォグァ", "フュ",
    # 장음
    "トーキョー", "キョー", "ホントーニ", "ウィンター", "オネーサン", "ラーメン",
    "トウキョウ", "オオサカ", "ユウキ", "センセイ", "ヘイセイ", "オカアサン", "オニイサン",
    # 촉음
    "ガッコー", "イッパイ", "マッテ", "ザッシ", "キッテ", "ジェット", "チョット", "ヤッタ",
    # 발음(ん)
    "サンポ", "センパイ", "リンゴ", "コンニチワ", "ニホン", "ホンヤ", "カンジ", "ミンナ",
    "ンン", "アン",
    # 단어
    "シラナイ", "アイシテル", "ハジメマシテ", "キミワ", "ジャナイ", "ツヨイ", "ミツケタ",
    "ドライブ", "ファイト", "ラヴ", "サヨナラ", "アリガトウ", "オヤスミ",
    # 히라가나 입력
    "きみは", "ありがとう", "がっこう", "きょう",
    # 가나 아닌 문자 통과
    "ドライブ (Drive)", "アイ、ラブ", "walking in the rain", "ABC", "", "。",
    # 요음 뒤 장음 / 작은 가나 처리
    "シュー", "リョー", "ニュー", "ファー", "ウォー", "ティー",
]


def main() -> None:
    vectors = []
    for kana in INPUTS:
        vectors.append(
            {
                "kana": kana,
                "pronunciation": to_hangul(kana, Mode.PRONUNCIATION),
                "official": to_hangul(kana, Mode.OFFICIAL),
                # 어중 위치(표기법 모드의 어두 평음화가 걸리지 않는 경우)
                "official_medial": to_hangul(kana, Mode.OFFICIAL, word_initial=False),
            }
        )
    json.dump(vectors, sys.stdout, ensure_ascii=False, indent=1)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
