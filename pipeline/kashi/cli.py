"""kashi 명령줄 도구."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from . import lrclib
from .cache import Cache
from .hangul import Mode
from .models import LyricDoc, make_track_id
from .pipeline import LyricsNotFound, build
from .translate import TranslationError, get_translator


def _load_overrides(path: str | None) -> dict[str, str]:
    """当て字 등 직접 지정한 읽기를 읽어들인다. 예: {"運命": "サダメ"}"""
    if not path:
        return {}
    return json.loads(Path(path).read_text("utf-8"))


def _print_doc(doc: LyricDoc, show_reading: bool) -> None:
    header = f"{doc.artist} - {doc.title}"
    print(header)
    print(
        f"{'싱크 있음' if doc.synced else '싱크 없음'} · {len(doc.lines)}줄 · "
        f"표기 {doc.hangul_mode} · 번역 {doc.translator} · {doc.source}"
    )
    print("-" * max(len(header), 40))

    for line in doc.lines:
        if not line.original.strip():
            print()
            continue
        stamp = f"[{int(line.time // 60):02d}:{line.time % 60:05.2f}] " if line.time is not None else ""
        print(f"{stamp}{line.original}")
        if show_reading and line.reading:
            print(f"{' ' * len(stamp)}  {line.reading}")
        if line.hangul:
            print(f"{' ' * len(stamp)}  {line.hangul}")
        if line.translation:
            print(f"{' ' * len(stamp)}  {line.translation}")
        print()


def cmd_search(args: argparse.Namespace) -> int:
    tracks = lrclib.search(query=args.query)
    if not tracks:
        print("검색 결과가 없습니다.", file=sys.stderr)
        return 1
    for track in tracks[: args.limit]:
        mark = "싱크" if track.synced_lyrics else "평문" if track.plain_lyrics else "없음"
        print(
            f"[{mark}] {track.artist} - {track.title}"
            f"  ({track.album or '앨범 미상'}, {int(track.duration)}초)"
        )
    return 0


def cmd_build(args: argparse.Namespace) -> int:
    mode = Mode(args.mode)
    cache = Cache(args.cache_dir) if args.cache_dir else Cache()
    track_id = make_track_id(args.artist, args.title, args.album)

    doc = None if args.refresh else cache.load(track_id, mode.value)
    if doc is None:
        try:
            translator = get_translator(args.translator)
        except TranslationError as exc:
            print(f"번역기를 준비하지 못했습니다: {exc}", file=sys.stderr)
            print("번역 없이 계속합니다. --translator none 으로 끌 수 있습니다.", file=sys.stderr)
            translator = get_translator("none")

        try:
            doc = build(
                artist=args.artist,
                title=args.title,
                album=args.album,
                duration=args.duration,
                mode=mode,
                translator=translator,
                overrides=_load_overrides(args.overrides),
                with_tokens=args.tokens,
            )
        except (LyricsNotFound, lrclib.LrclibError) as exc:
            print(str(exc), file=sys.stderr)
            return 1
        # 다음에 같은 이름으로 물어봐도 맞도록 요청에 쓴 키로도 남긴다.
        cache.save(doc, extra_ids=[track_id])

    if args.out:
        Path(args.out).write_text(
            json.dumps(doc.to_dict(), ensure_ascii=False, indent=2), "utf-8"
        )
        print(f"저장했습니다: {args.out}", file=sys.stderr)
    if args.json:
        print(json.dumps(doc.to_dict(), ensure_ascii=False, indent=2))
    else:
        _print_doc(doc, show_reading=args.reading)
    return 0


def cmd_serve(args: argparse.Namespace) -> int:
    from .server import serve

    serve(host=args.host, port=args.port, translator_name=args.translator)
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="kashi", description="일본어 가사를 원문 + 한글 발음 + 뜻으로 가공합니다."
    )
    sub = parser.add_subparsers(dest="command", required=True)

    search = sub.add_parser("search", help="LRCLIB 에서 곡을 찾습니다")
    search.add_argument("query")
    search.add_argument("--limit", type=int, default=10)
    search.set_defaults(func=cmd_search)

    make = sub.add_parser("build", help="가사를 가공해 캐시에 넣고 보여줍니다")
    make.add_argument("--artist", required=True)
    make.add_argument("--title", required=True)
    make.add_argument("--album", default="")
    make.add_argument("--duration", type=float, default=None, help="재생 길이(초). 주면 매칭이 정확해집니다")
    make.add_argument("--mode", choices=[m.value for m in Mode], default=Mode.PRONUNCIATION.value)
    make.add_argument("--translator", default="auto", help="auto | none | claude | deepl | papago")
    make.add_argument("--overrides", help="읽기를 직접 지정한 JSON 파일")
    make.add_argument("--tokens", action="store_true", help="단어별 읽기까지 담습니다")
    make.add_argument("--reading", action="store_true", help="가나 읽기도 함께 출력합니다")
    make.add_argument("--json", action="store_true")
    make.add_argument("--out", help="JSON 을 파일로 저장합니다")
    make.add_argument("--refresh", action="store_true", help="캐시를 무시하고 다시 만듭니다")
    make.add_argument("--cache-dir")
    make.set_defaults(func=cmd_build)

    server = sub.add_parser("serve", help="안드로이드 앱이 붙을 HTTP 서버를 띄웁니다")
    server.add_argument("--host", default="0.0.0.0")
    server.add_argument("--port", type=int, default=8765)
    server.add_argument("--translator", default="auto")
    server.set_defaults(func=cmd_serve)

    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    raise SystemExit(main())
