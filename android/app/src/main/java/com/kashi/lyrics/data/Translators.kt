package com.kashi.lyrics.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/**
 * 가사 -> 한국어 뜻. pipeline/kashi/translate.py 와 같은 규칙이다.
 *
 * 가사는 주어가 자주 빠지고 비유가 많아 줄 단위로 던지면 번역이 무너진다. 곡 전체를 한 번에
 * 보내고 줄 번호로 돌려받는다. 곡당 한 번이라 비용도 문제되지 않는다.
 */
class TranslationException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface Translator {
    val name: String

    /** 입력과 같은 길이의 목록을 돌려준다. 빈 줄(간주)은 빈 문자열이다. */
    @Throws(TranslationException::class)
    fun translateLines(lines: List<String>): List<String>

    object None : Translator {
        override val name = "none"
        override fun translateLines(lines: List<String>) = lines.map { "" }
    }

    companion object {
        const val PROVIDER_NONE = "none"
        const val PROVIDER_CLAUDE = "claude"
        const val PROVIDER_DEEPL = "deepl"
        const val PROVIDER_PAPAGO = "papago"

        val PROVIDERS = listOf(PROVIDER_NONE, PROVIDER_CLAUDE, PROVIDER_DEEPL, PROVIDER_PAPAGO)

        /** 설정값으로 번역기를 만든다. 키가 비어 있으면 [None]. */
        fun from(provider: String, key: String, secret: String = ""): Translator = when {
            key.isBlank() -> None
            provider == PROVIDER_CLAUDE -> ClaudeTranslator(key)
            provider == PROVIDER_DEEPL -> DeepLTranslator(key)
            provider == PROVIDER_PAPAGO && secret.isNotBlank() -> PapagoTranslator(key, secret)
            else -> None
        }

        /** 빈 줄(간주)은 번역기에 보내지 않는다. {원래 위치: 줄} */
        internal fun translatable(lines: List<String>): Map<Int, String> =
            lines.withIndex().filter { it.value.isNotBlank() }.associate { it.index to it.value }

        @Throws(TranslationException::class)
        internal fun post(url: String, headers: Map<String, String>, body: ByteArray): JSONObject {
            val connection = try {
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15_000
                    readTimeout = 120_000
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                }
            } catch (e: IOException) {
                throw TranslationException("$url 에 연결하지 못했습니다", e)
            }
            try {
                connection.outputStream.use { it.write(body) }
                val code = connection.responseCode
                if (code !in 200..299) {
                    val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }?.take(400)
                    throw TranslationException("$url -> $code: $detail")
                }
                return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } catch (e: IOException) {
                throw TranslationException("$url 요청이 실패했습니다", e)
            } finally {
                connection.disconnect()
            }
        }
    }
}

class ClaudeTranslator(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) : Translator {
    override val name = Translator.PROVIDER_CLAUDE

    override fun translateLines(lines: List<String>): List<String> {
        val targets = Translator.translatable(lines)
        if (targets.isEmpty()) return lines.map { "" }

        val numbered = targets.entries.joinToString("\n") { (i, text) -> "$i\t$text" }
        val payload = JSONObject()
            .put("model", model)
            .put("max_tokens", 8192)
            .put("system", SYSTEM_PROMPT)
            .put(
                "messages", JSONArray()
                    .put(JSONObject().put("role", "user").put("content", "다음 가사를 번역하세요.\n\n$numbered"))
                    // 응답을 JSON 으로 시작하게 고정해 코드펜스가 섞이는 것을 막는다.
                    .put(JSONObject().put("role", "assistant").put("content", "{"))
            )

        val data = Translator.post(
            "https://api.anthropic.com/v1/messages",
            mapOf(
                "content-type" to "application/json",
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01",
            ),
            payload.toString().toByteArray(),
        )
        val content = data.optJSONArray("content") ?: JSONArray()
        val text = "{" + (0 until content.length()).joinToString("") { content.getJSONObject(it).optString("text") }
        return parseMapping(text, lines.size)
    }

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5"

        internal fun parseMapping(text: String, lineCount: Int): List<String> {
            val end = text.lastIndexOf('}')
            if (end < 0) throw TranslationException("번역 응답을 JSON 으로 읽지 못했습니다: ${text.take(200)}")
            val mapping = try {
                JSONObject(text.substring(0, end + 1))
            } catch (e: Exception) {
                throw TranslationException("번역 응답을 JSON 으로 읽지 못했습니다: ${text.take(200)}", e)
            }
            return (0 until lineCount).map { mapping.optString(it.toString(), "") }
        }

        val SYSTEM_PROMPT = """
            당신은 일본어 노래 가사를 한국어로 옮기는 번역가입니다.

            규칙:
            - 각 줄을 자연스러운 한국어로 옮기되, 줄 번호와 줄 수를 그대로 유지합니다.
            - 가사 전체의 문맥을 보고 번역합니다. 앞뒤 줄에 걸친 문장은 이어서 이해하되,
              출력은 원문 줄 경계를 그대로 따릅니다.
            - 의역보다 뜻이 분명히 드러나는 쪽을 고릅니다. 학습용이라 원문 구조가 보이는 편이 좋습니다.
            - 원문에 있는 영어 가사는 한국어로 옮깁니다.
            - 설명, 주석, 코드펜스를 덧붙이지 않습니다.

            출력은 JSON 객체 하나입니다. 키는 줄 번호 문자열, 값은 번역문입니다.
            예: {"0": "첫 줄 번역", "1": "둘째 줄 번역"}
        """.trimIndent()
    }
}

class DeepLTranslator(private val apiKey: String) : Translator {
    override val name = Translator.PROVIDER_DEEPL

    // 무료 키는 ':fx' 로 끝나고 호스트가 다르다.
    private val url = if (apiKey.endsWith(":fx")) "https://api-free.deepl.com/v2/translate"
    else "https://api.deepl.com/v2/translate"

    override fun translateLines(lines: List<String>): List<String> {
        val targets = Translator.translatable(lines)
        if (targets.isEmpty()) return lines.map { "" }

        val payload = JSONObject()
            .put("text", JSONArray(targets.values.toList()))
            .put("source_lang", "JA")
            .put("target_lang", "KO")
        val data = Translator.post(
            url,
            mapOf("content-type" to "application/json", "authorization" to "DeepL-Auth-Key $apiKey"),
            payload.toString().toByteArray(),
        )
        val translations = data.optJSONArray("translations") ?: JSONArray()
        val out = MutableList(lines.size) { "" }
        targets.keys.forEachIndexed { n, index ->
            if (n < translations.length()) out[index] = translations.getJSONObject(n).optString("text")
        }
        return out
    }
}

class PapagoTranslator(private val clientId: String, private val clientSecret: String) : Translator {
    override val name = Translator.PROVIDER_PAPAGO

    override fun translateLines(lines: List<String>): List<String> {
        val targets = Translator.translatable(lines)
        if (targets.isEmpty()) return lines.map { "" }

        // 파파고는 한 번에 한 덩어리만 받는다. 줄을 이어 보내고 다시 쪼갠다.
        val form = "source=ja&target=ko&text=" + URLEncoder.encode(targets.values.joinToString("\n"), "UTF-8")
        val data = Translator.post(
            "https://naveropenapi.apigw.ntruss.com/nmt/v1/translation",
            mapOf(
                "content-type" to "application/x-www-form-urlencoded; charset=UTF-8",
                "x-ncp-apigw-api-key-id" to clientId,
                "x-ncp-apigw-api-key" to clientSecret,
            ),
            form.toByteArray(),
        )
        val translated = data.optJSONObject("message")?.optJSONObject("result")
            ?.optString("translatedText").orEmpty().split("\n")

        val out = MutableList(lines.size) { "" }
        // 줄 수가 어긋나면 잘못 붙이느니 비워 두는 편이 낫다.
        if (translated.size != targets.size) return out
        targets.keys.forEachIndexed { n, index -> out[index] = translated[n].trim() }
        return out
    }
}
