"""shared/hangul_vectors.json 이 현재 Python 구현과 일치하는지 본다.

규칙을 바꿨다면 `python -m tools.gen_vectors > ../shared/hangul_vectors.json` 으로
벡터를 다시 만들고, 안드로이드 쪽 HangulVectorsTest 도 통과하는지 확인한다.
"""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from kashi.hangul import Mode, to_hangul  # noqa: E402

VECTORS = Path(__file__).resolve().parents[2] / "shared" / "hangul_vectors.json"


def test_vectors_match_current_implementation():
    vectors = json.loads(VECTORS.read_text("utf-8"))
    assert len(vectors) > 50

    mismatches = []
    for v in vectors:
        actual = {
            "pronunciation": to_hangul(v["kana"], Mode.PRONUNCIATION),
            "official": to_hangul(v["kana"], Mode.OFFICIAL),
            "official_medial": to_hangul(v["kana"], Mode.OFFICIAL, word_initial=False),
        }
        for field, value in actual.items():
            if v[field] != value:
                mismatches.append(f"{v['kana']} [{field}]: 벡터 {v[field]!r} 현재 {value!r}")
    assert not mismatches, "벡터가 낡았습니다. gen_vectors 를 다시 돌리세요:\n" + "\n".join(mismatches)
