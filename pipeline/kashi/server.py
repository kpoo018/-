"""안드로이드 앱이 붙는 작은 HTTP 서버.

표준 라이브러리만 쓴다. 집 안 네트워크에서 PC 한 대에 띄워 두고 폰이 붙는 것을
전제로 한다. 인증이 없으므로 공개된 곳에 그대로 노출하지 않는다.

    GET /health
    GET /lyrics?artist=..&title=..[&album=..][&duration=..][&mode=..][&tokens=1][&refresh=1]
"""

from __future__ import annotations

import json
import logging
import urllib.parse
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from . import lrclib
from .cache import Cache
from .hangul import Mode
from .models import make_track_id
from .pipeline import LyricsNotFound, build
from .translate import TranslationError, Translator, get_translator

log = logging.getLogger("kashi.server")


class Handler(BaseHTTPRequestHandler):
    server_version = "kashi/0.1"
    cache: Cache
    translator: Translator

    def do_GET(self) -> None:  # noqa: N802 - BaseHTTPRequestHandler 규약
        parsed = urllib.parse.urlparse(self.path)
        params = urllib.parse.parse_qs(parsed.query)

        if parsed.path == "/health":
            self._send(HTTPStatus.OK, {"ok": True, "translator": self.translator.name})
            return
        if parsed.path != "/lyrics":
            self._send(HTTPStatus.NOT_FOUND, {"error": "그런 경로는 없습니다"})
            return

        artist = self._one(params, "artist")
        title = self._one(params, "title")
        if not (artist and title):
            self._send(HTTPStatus.BAD_REQUEST, {"error": "artist 와 title 이 필요합니다"})
            return

        try:
            mode = Mode(self._one(params, "mode") or Mode.PRONUNCIATION.value)
        except ValueError:
            self._send(HTTPStatus.BAD_REQUEST, {"error": "mode 값이 잘못되었습니다"})
            return

        album = self._one(params, "album")
        duration = self._float(self._one(params, "duration"))
        track_id = make_track_id(artist, title, album)

        if not self._one(params, "refresh"):
            cached = self.cache.load(track_id, mode.value)
            if cached is not None:
                self._send(HTTPStatus.OK, cached.to_dict())
                return

        try:
            doc = build(
                artist=artist,
                title=title,
                album=album,
                duration=duration,
                mode=mode,
                translator=self.translator,
                with_tokens=bool(self._one(params, "tokens")),
            )
        except LyricsNotFound as exc:
            self._send(HTTPStatus.NOT_FOUND, {"error": str(exc)})
            return
        except (lrclib.LrclibError, TranslationError) as exc:
            self._send(HTTPStatus.BAD_GATEWAY, {"error": str(exc)})
            return

        self.cache.save(doc)
        self._send(HTTPStatus.OK, doc.to_dict())

    @staticmethod
    def _one(params: dict[str, list[str]], key: str) -> str:
        return (params.get(key) or [""])[0].strip()

    @staticmethod
    def _float(value: str) -> float | None:
        try:
            return float(value) if value else None
        except ValueError:
            return None

    def _send(self, status: HTTPStatus, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args) -> None:  # noqa: A002
        log.info("%s - %s", self.address_string(), format % args)


def serve(host: str = "0.0.0.0", port: int = 8765, translator_name: str = "auto") -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(message)s")

    try:
        translator = get_translator(translator_name)
    except TranslationError as exc:
        log.warning("번역기를 준비하지 못했습니다 (%s). 번역 없이 띄웁니다.", exc)
        translator = get_translator("none")

    Handler.cache = Cache()
    Handler.translator = translator

    httpd = ThreadingHTTPServer((host, port), Handler)
    log.info("http://%s:%d 에서 대기 중 (번역기: %s)", host, port, translator.name)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        log.info("종료합니다.")
    finally:
        httpd.server_close()
