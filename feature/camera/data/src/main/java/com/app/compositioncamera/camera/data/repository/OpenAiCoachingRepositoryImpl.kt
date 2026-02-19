package com.app.compositioncamera.camera.data.repository

import com.app.compositioncamera.camera.data.BuildConfig
import com.app.compositioncamera.camera.domain.model.CompositionMetrics
import com.app.compositioncamera.camera.domain.repository.AiCoachingRepository
import com.app.compositioncamera.util.Logx
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class OpenAiCoachingRepositoryImpl @Inject constructor() : AiCoachingRepository {

    override suspend fun getCoaching(metrics: CompositionMetrics): String? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.OPENAI_API_KEY
        if (apiKey.isBlank()) {
            Logx.w("OPENAI_API_KEY is blank", tag = TAG)
            return@withContext null
        }
        Logx.d("OpenAI key loaded. length=${apiKey.length}", tag = TAG)

        val connection = (URL(OPENAI_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 2_000
            readTimeout = 2_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        return@withContext runCatching {
            val body = buildRequestBody(metrics)
            Logx.d("Request metrics: $metrics", tag = TAG)
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body.toString())
            }

            val responseCode = connection.responseCode
            Logx.d("OpenAI responseCode=$responseCode", tag = TAG)
            if (responseCode !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Logx.e(
                    message = "OpenAI request failed. code=$responseCode body=${errorBody ?: "empty"}",
                    tag = TAG
                )
                return@runCatching null
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            Logx.d(
                message = "OpenAI success response received. size=${responseText.length}",
                tag = TAG
            )
            val root = JSONObject(responseText)
            val choices = root.optJSONArray("choices") ?: JSONArray()
            if (choices.length() == 0) {
                Logx.w("OpenAI response has no choices", tag = TAG)
                return@runCatching null
            }

            val message = choices.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()

            if (message.isNullOrBlank()) {
                Logx.w("OpenAI message content is blank", tag = TAG)
                null
            } else {
                Logx.d("OpenAI coaching parsed: $message", tag = TAG)
                message
            }
        }.onFailure { throwable ->
            Logx.e(
                throwable = throwable,
                message = "OpenAI request exception",
                tag = TAG
            )
        }.getOrNull()
    }

    private fun buildRequestBody(metrics: CompositionMetrics): JSONObject {
        val systemPrompt = """
            너는 카메라 구도 코치다.
            한국어로 1문장만 답하고, 반드시 행동 지시로 끝낸다.
            30자 이내로 짧게 작성한다.
        """.trimIndent()

        val userPrompt = """
            mode=${metrics.mode}
            rollDeg=${"%.1f".format(metrics.rollDeg)}
            score=${metrics.compositionScore}
            personCount=${metrics.personCount}
            objectCount=${metrics.objectCount}
            primaryObject=${metrics.primaryObjectLabel ?: "none"}
            이 상태에서 사진이 더 좋아지도록 한 문장 코칭을 줘.
        """.trimIndent()

        return JSONObject().apply {
            put("model", BuildConfig.OPENAI_MODEL)
            put("temperature", 0.4)
            put("max_tokens", 40)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt))
            )
        }
    }

    private companion object {
        const val TAG = "OpenAiCoach"
        const val OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions"
    }
}
