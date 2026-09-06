"""처리 끝난 가사 문서를 디스크에 담아 둔다.

형태소 분석과 번역은 곡당 한 번만 하면 된다. 재생 중에는 캐시만 읽는다.
"""

from __future__ import annotations

import json
import os
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

    def save(self, doc: LyricDoc) -> Path:
        path = self.path(doc.track_id, doc.hangul_mode)
        path.parent.mkdir(parents=True, exist_ok=True)
        # 부분적으로 쓰인 파일이 남지 않도록 임시 파일에 쓰고 옮긴다.
        temporary = path.with_suffix(".json.tmp")
        temporary.write_text(
            json.dumps(doc.to_dict(), ensure_ascii=False, indent=2), "utf-8"
        )
        temporary.replace(path)
        return path
