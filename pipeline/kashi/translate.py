"""가사 -> 한국어 뜻. 번역기는 갈아끼울 수 있게 해 둔다.

가사는 주어가 자주 빠지고 비유가 많아서 줄을 하나씩 던지면 번역이 무너진다. 모든
번역기에 곡 전체를 한 번에 넘기고 줄 단위로 돌려받는다. 곡당 한 번만 호출하므로
비용도 문제되지 않는다.

사용 가능한 백엔드:

- ``none``   번역 없이 원문/발음만 만든다. 키가 하나도 없어도 파이프라인이 돈다.
- ``claude`` ANTHROPIC_API_KEY. 문맥을 살리므로 가사에는 이쪽이 가장 낫다.
- ``deepl``  DEEPL_API_KEY
- ``papago`` PAPAGO_CLIENT_ID + PAPAGO_CLIENT_SECRET (네이버 클라우드 플랫폼)
"""

from __future__ import annotations

import json
import os
import urllib.error
import urllib.parse
import urllib.request

TIMEOUT = 120
DEFAULT_CLAUDE_MODEL = "claude-sonnet-5"

_SYSTEM_PROMPT = """당신은 일본어 노래 가사를 한국어로 옮기는 번역가입니다.

규칙:
- 각 줄을 자연스러운 한국어로 옮기되, 줄 번호와 줄 수를 그대로 유지합니다.
- 가사 전체의 문맥을 보고 번역합니다. 앞뒤 줄에 걸친 문장은 이어서 이해하되,
  출력은 원문 줄 경계를 그대로 따릅니다.
- 의역보다 뜻이 분명히 드러나는 쪽을 고릅니다. 학습용이라 원문 구조가 보이는 편이 좋습니다.
- 원문에 있는 영어 가사는 한국어로 옮깁니다.
- 설명, 주석, 코드펜스를 덧붙이지 않습니다.

출력은 JSON 객체 하나입니다. 키는 줄 번호 문자열, 값은 번역문입니다.
예: {"0": "첫 줄 번역", "1": "둘째 줄 번역"}"""


class TranslationError(RuntimeError):
    pass


class Translator:
    """모든 번역기의 공통 인터페이스."""

    name = "none"

    def translate_lines(self, lines: list[str]) -> list[str]:
        return [""] * len(lines)

    def _translatable(self, lines: list[str]) -> dict[int, str]:
        """빈 줄(간주)은 번역기에 보내지 않는다."""
        return {i: line for i, line in enumerate(lines) if line.strip()}


def _post(url: str, headers: dict[str, str], payload: bytes) -> dict:
    request = urllib.request.Request(url, data=payload, headers=headers, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", "replace")[:400]
        raise TranslationError(f"{url} -> {exc.code}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise TranslationError(f"{url} 에 연결하지 못했습니다: {exc.reason}") from exc


class ClaudeTranslator(Translator):
    name = "claude"

    def __init__(self, api_key: str | None = None, model: str | None = None):
        self.api_key = api_key or os.environ.get("ANTHROPIC_API_KEY", "")
        self.model = model or os.environ.get("KASHI_CLAUDE_MODEL", DEFAULT_CLAUDE_MODEL)
        if not self.api_key:
            raise TranslationError("ANTHROPIC_API_KEY 가 설정되어 있지 않습니다.")

    def translate_lines(self, lines: list[str]) -> list[str]:
        targets = self._translatable(lines)
        if not targets:
            return [""] * len(lines)

        numbered = "\n".join(f"{i}\t{text}" for i, text in targets.items())
        body = json.dumps(
            {
                "model": self.model,
                "max_tokens": 8192,
                "system": _SYSTEM_PROMPT,
                "messages": [
                    {
                        "role": "user",
                        "content": f"다음 가사를 번역하세요.\n\n{numbered}",
                    },
                    # 응답을 JSON 으로 시작하게 고정해 코드펜스가 섞이는 것을 막는다.
                    {"role": "assistant", "content": "{"},
                ],
            }
        ).encode("utf-8")

        data = _post(
            "https://api.anthropic.com/v1/messages",
            {
                "content-type": "application/json",
                "x-api-key": self.api_key,
                "anthropic-version": "2023-06-01",
            },
            body,
        )
        text = "{" + "".join(
            block.get("text", "") for block in data.get("content", [])
        )
        try:
            mapping = json.loads(text[: text.rindex("}") + 1])
        except (ValueError, json.JSONDecodeError) as exc:
            raise TranslationError(f"번역 응답을 JSON 으로 읽지 못했습니다: {text[:200]}") from exc

        return [str(mapping.get(str(i), "")) for i in range(len(lines))]


class DeepLTranslator(Translator):
    name = "deepl"

    def __init__(self, api_key: str | None = None):
        self.api_key = api_key or os.environ.get("DEEPL_API_KEY", "")
        if not self.api_key:
            raise TranslationError("DEEPL_API_KEY 가 설정되어 있지 않습니다.")
        # 무료 키는 ':fx' 로 끝나고 호스트가 다르다.
        host = "api-free" if self.api_key.endswith(":fx") else "api"
        self.url = f"https://{host}.deepl.com/v2/translate"

    def translate_lines(self, lines: list[str]) -> list[str]:
        targets = self._translatable(lines)
        if not targets:
            return [""] * len(lines)

        data = _post(
            self.url,
            {
                "content-type": "application/json",
                "authorization": f"DeepL-Auth-Key {self.api_key}",
            },
            json.dumps(
                {
                    "text": list(targets.values()),
                    "source_lang": "JA",
                    "target_lang": "KO",
                }
            ).encode("utf-8"),
        )
        translated = [item.get("text", "") for item in data.get("translations", [])]
        out = [""] * len(lines)
        for index, text in zip(targets, translated):
            out[index] = text
        return out


class PapagoTranslator(Translator):
    name = "papago"
    URL = "https://naveropenapi.apigw.ntruss.com/nmt/v1/translation"
    # 파파고는 한 번에 한 덩어리만 받는다. 줄을 이어 보내고 다시 쪼갠다.
    _SEPARATOR = "\n"

    def __init__(self, client_id: str | None = None, client_secret: str | None = None):
        self.client_id = client_id or os.environ.get("PAPAGO_CLIENT_ID", "")
        self.client_secret = client_secret or os.environ.get("PAPAGO_CLIENT_SECRET", "")
        if not (self.client_id and self.client_secret):
            raise TranslationError(
                "PAPAGO_CLIENT_ID 와 PAPAGO_CLIENT_SECRET 이 필요합니다."
            )

    def translate_lines(self, lines: list[str]) -> list[str]:
        targets = self._translatable(lines)
        if not targets:
            return [""] * len(lines)

        data = _post(
            self.URL,
            {
                "content-type": "application/x-www-form-urlencoded; charset=UTF-8",
                "x-ncp-apigw-api-key-id": self.client_id,
                "x-ncp-apigw-api-key": self.client_secret,
            },
            urllib.parse.urlencode(
                {
                    "source": "ja",
                    "target": "ko",
                    "text": self._SEPARATOR.join(targets.values()),
                }
            ).encode("utf-8"),
        )
        translated = (
            data.get("message", {}).get("result", {}).get("translatedText", "")
        ).split(self._SEPARATOR)

        out = [""] * len(lines)
        if len(translated) != len(targets):
            # 줄 수가 어긋나면 잘못 붙이느니 비워 두는 편이 낫다.
            return out
        for index, text in zip(targets, translated):
            out[index] = text.strip()
        return out


BACKENDS = {
    "none": Translator,
    "claude": ClaudeTranslator,
    "deepl": DeepLTranslator,
    "papago": PapagoTranslator,
}


def get_translator(name: str = "auto") -> Translator:
    """이름으로 번역기를 만든다. ``auto`` 는 환경변수를 보고 알아서 고른다."""
    if name == "auto":
        for candidate in ("claude", "deepl", "papago"):
            try:
                return BACKENDS[candidate]()  # type: ignore[abstract]
            except TranslationError:
                continue
        return Translator()

    if name not in BACKENDS:
        raise TranslationError(
            f"모르는 번역기입니다: {name} (가능한 값: {', '.join(BACKENDS)})"
        )
    return BACKENDS[name]()  # type: ignore[abstract]
