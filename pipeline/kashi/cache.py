"""처리 끝난 가사 문서를 디스크에 담아 둔다.

형태소 분석과 번역은 곡당 한 번만 하면 된다. 재생 중에는 캐시만 읽는다.
"""

from __future__ import annotations

import json
import os
from collections.abc import Iterable
from pathlib import Path

from .models import LyricDoc

DEFAULT_ROOT = Path(
    os.environ.get("KASHI_CACHE_DIR", Path.home() / ".cache" / "kashi")
)


class Cache:
    def __init__(self, root: Path | str = DEFAULT_ROOT):
        self.root = Path(root)

    def path(self, track_id: str, hangul_mode: str) -> Path:
        return self.root / hangul_mode / f"{track_id}.json"

    def load(self, track_id: str, hangul_mode: str) -> LyricDoc | None:
        path = self.path(track_id, hangul_mode)
        if not path.exists():
            return None
        try:
            return LyricDoc.from_dict(json.loads(path.read_text("utf-8")))
        except (ValueError, TypeError, OSError):
            # 캐시가 깨졌으면 없는 셈 치고 다시 만든다.
            return None

    def save(self, doc: LyricDoc, extra_ids: Iterable[str] = ()) -> Path:
        """문서를 저장한다.

        ``doc.track_id`` 는 LRCLIB 이 돌려준 정규 표기로 만들어진다. 조회는 사용자나
        플레이어가 준 이름으로 들어오므로 그대로 두면 캐시가 영영 맞지 않는다.
        요청에 쓰인 키를 ``extra_ids`` 로 함께 받아 같은 내용을 그 이름으로도 남긴다.
        """
        body = json.dumps(doc.to_dict(), ensure_ascii=False, indent=2)
        ids = [doc.track_id, *(i for i in extra_ids if i and i != doc.track_id)]

        for track_id in ids:
            path = self.path(track_id, doc.hangul_mode)
            path.parent.mkdir(parents=True, exist_ok=True)
            # 부분적으로 쓰인 파일이 남지 않도록 임시 파일에 쓰고 옮긴다.
            temporary = path.with_suffix(".json.tmp")
            temporary.write_text(body, "utf-8")
            temporary.replace(path)

        return self.path(ids[0], doc.hangul_mode)
