package com.vineyard.editoraPRO.app.data.remote

import com.vineyard.editoraPRO.app.data.model.GeminiModelInfo
import com.vineyard.editoraPRO.app.data.model.GeminiPart
import com.vineyard.editoraPRO.app.security.SecurityGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GeminiHttpException(val statusCode: Int, message: String) : IOException(message)

class VertexAiService(
    private val httpClient: OkHttpClient = defaultStreamingClient()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchLiveModels(
        apiKey: String,
        onLog: (String) -> Unit
    ): List<GeminiModelInfo> = coroutineScope {
        val project = SecurityGuard.getVertexProject()
        val location = SecurityGuard.getVertexLocation()
        val base = SecurityGuard.getVertexBaseUrl()
        onLog("Probing live Gemini Platform models on $project/$location via :countTokens...")

        data class ProbeResult(
            val cand: GeminiModelInfo,
            val ok: Boolean,
            val status: Int,
            val message: String
        )

        val checks = CANDIDATE_MODELS.map { cand ->
            async(Dispatchers.IO) {
                val url = "$base/${URLEncoder.encode(cand.name, "UTF-8")}:countTokens"
                val payload = buildJsonObject {
                    put("contents", buildJsonArray {
                        add(buildJsonObject {
                            put("role", "user")
                            put("parts", buildJsonArray {
                                add(buildJsonObject { put("text", "ping") })
                            })
                        })
                    })
                }.toString()

                val req = Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .post(payload.toRequestBody(JSON_MEDIA))
                    .build()

                val call = httpClient.newCall(req)
                val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }

                try {
                    call.execute().use { resp ->
                        val bodyStr = resp.body?.string().orEmpty()
                        val errMsg = extractErrorMessage(bodyStr) ?: resp.message
                        ProbeResult(cand, resp.isSuccessful, resp.code, errMsg)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (!coroutineContext.isActive) throw CancellationException("Cancelled by user.")
                    ProbeResult(cand, false, 0, e.message ?: "Network error")
                } finally {
                    cancelHandle?.dispose()
                }
            }
        }.awaitAll()

        val available = checks.filter { it.ok }.map {
            it.cand.copy(
                description = "Verified live on Gemini Platform ($project/$location)"
            )
        }

        if (available.isEmpty()) {
            val priority = checks.firstOrNull { it.status in listOf(401, 403, 429) }
                ?: checks.firstOrNull { it.status >= 500 }
                ?: checks.first()
            throw GeminiHttpException(
                priority.status,
                "[HTTP ${priority.status}] ${priority.message.ifBlank { "No live models available" }}"
            )
        }
        available
    }

    suspend fun streamGenerateContent(
        modelName: String,
        parts: List<GeminiPart>,
        apiKey: String,
        useThinkingConfig: Boolean = true,
        onStatus: (String, Float?) -> Unit,
        onLog: (String) -> Unit,
        onToken: (text: String, isThought: Boolean) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val modelId = modelName.removePrefix("models/")
        val project = SecurityGuard.getVertexProject()
        val location = SecurityGuard.getVertexLocation()
        val base = SecurityGuard.getVertexBaseUrl()
        val url = "$base/${URLEncoder.encode(modelId, "UTF-8")}:streamGenerateContent?alt=sse"

        val genCfg = buildJsonObject {
            put("temperature", 0.2)
            if (useThinkingConfig) {
                put("thinkingConfig", buildJsonObject {
                    put("includeThoughts", true)
                })
            }
        }

        val bodyJson = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", parts.toJsonArray())
                })
            })
            put("generationConfig", genCfg)
        }.toString()

        val reqStart = System.nanoTime()
        onLog("POST $project/$location/models/$modelId:streamGenerateContent?alt=sse (thoughts=$useThinkingConfig)")
        onStatus("Requesting $modelId...", 0.42f)

        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", apiKey)
            .post(bodyJson.toRequestBody(JSON_MEDIA))
            .build()

        val call = httpClient.newCall(request)
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion { call.cancel() }
        val gotAnyData = AtomicBoolean(false)

        val watchdogThread = Thread {
            try {
                Thread.sleep(90_000L)
                if (!gotAnyData.get()) {
                    call.cancel()
                }
            } catch (_: InterruptedException) {
            }
        }.apply { isDaemon = true; start() }

        try {
            call.execute().use { resp ->
                if (!resp.isSuccessful) {
                    val errText = resp.body?.string().orEmpty()
                    val errMsg = extractErrorMessage(errText) ?: "Request failed"
                    if (useThinkingConfig && resp.code == 400 &&
                        Regex("thinking|include_thoughts|thinkingConfig", RegexOption.IGNORE_CASE).containsMatchIn(errMsg)
                    ) {
                        onLog("Model doesn't use thinkingConfig ($errMsg) → Retrying without thinkingConfig...")
                        watchdogThread.interrupt()
                        return@withContext streamGenerateContent(
                            modelName = modelName,
                            parts = parts,
                            apiKey = apiKey,
                            useThinkingConfig = false,
                            onStatus = onStatus,
                            onLog = onLog,
                            onToken = onToken
                        )
                    }
                    throw GeminiHttpException(resp.code, "[HTTP ${resp.code}] $errMsg")
                }

                val connectSec = (System.nanoTime() - reqStart) / 1_000_000_000.0
                onLog("Stream connected (HTTP ${resp.code}) in ${"%.2f".format(connectSec)}s. Reading live SSE...")
                onStatus("Streaming from $modelId...", 0.46f)

                val source = resp.body?.source() ?: throw IOException("Empty SSE response body")
                val fullOutput = StringBuilder()
                var gotFirstToken = false
                var chunkCount = 0

                while (true) {
                    val line = source.readUtf8Line() ?: break
                    gotAnyData.set(true)
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("data:")) continue
                    val payload = trimmed.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue

                    val chunkObj = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: continue
                    val errObj = chunkObj["error"]?.jsonObject
                    if (errObj != null) {
                        val code = errObj["code"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 500
                        val msg = errObj["message"]?.jsonPrimitive?.contentOrNull ?: "Stream error"
                        throw GeminiHttpException(code, "[HTTP $code] $msg")
                    }

                    val respParts = chunkObj["candidates"]?.jsonArray
                        ?.firstOrNull()?.jsonObject
                        ?.get("content")?.jsonObject
                        ?.get("parts")?.jsonArray ?: continue

                    for (el in respParts) {
                        val pObj = el.jsonObject
                        val text = pObj["text"]?.jsonPrimitive?.contentOrNull ?: continue
                        if (!gotFirstToken) {
                            gotFirstToken = true
                            val ttfb = (System.nanoTime() - reqStart) / 1_000_000_000.0
                            onLog("First AI token arrived in ${"%.2f".format(ttfb)}s from $modelId!")
                        }
                        chunkCount++
                        val isThought = pObj["thought"]?.jsonPrimitive?.contentOrNull == "true"
                        if (isThought) {
                            onStatus("AI is reasoning...", null)
                            onToken(text, true)
                        } else {
                            onStatus("AI is reporting & synthesizing...", null)
                            onToken(text, false)
                            fullOutput.append(text)
                        }
                    }
                }

                val totalSec = (System.nanoTime() - reqStart) / 1_000_000_000.0
                val result = fullOutput.toString()
                onLog("Stream completed in ${"%.2f".format(totalSec)}s ($chunkCount chunks, ${result.length} chars).")
                if (result.isBlank()) throw IOException("Stream ended with empty output.")
                result
            }
        } catch (e: IOException) {
            if (!coroutineContext.isActive) {
                throw CancellationException("Cancelled by user.")
            }
            if (!gotAnyData.get() && call.isCanceled()) {
                throw GeminiHttpException(408, "Model \"$modelId\" did not respond within 90s")
            }
            throw e
        } finally {
            cancelHandle?.dispose()
            watchdogThread.interrupt()
        }
    }

    private fun extractErrorMessage(body: String): String? {
        if (body.isBlank()) return null
        return runCatching {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        val CANDIDATE_MODELS = listOf(
            GeminiModelInfo("gemini-4-neon", "Gemini 4 Neon"),
            GeminiModelInfo("gemini-4-hydrogen", "Gemini 4 Hydrogen"),
            GeminiModelInfo("gemini-4-carbon", "Gemini 4 Carbon"),
            GeminiModelInfo("gemini-4-argon", "Gemini 4 Argon"),
            GeminiModelInfo("gemini-3.8-flash", "Gemini 3.8 Flash"),
            GeminiModelInfo("gemini-3.5-flash", "Gemini 3.5 Flash"),
            GeminiModelInfo("gemini-3.5-flash-lite", "Gemini 3.5 Flash Lite"),
            GeminiModelInfo("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview"),
            GeminiModelInfo("gemini-2.5-flash", "Gemini 2.5 Flash"),
            GeminiModelInfo("gemini-2.5-pro", "Gemini 2.5 Pro")
        )

        fun defaultStreamingClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

internal fun List<GeminiPart>.toJsonArray(): JsonArray = buildJsonArray {
    for (part in this@toJsonArray) {
        when (part) {
            is GeminiPart.Text -> add(buildJsonObject { put("text", part.text) })
            is GeminiPart.InlineData -> add(buildJsonObject {
                put("inlineData", buildJsonObject {
                    put("mimeType", part.mimeType)
                    put("data", part.base64Data)
                })
            })
            is GeminiPart.FileData -> add(buildJsonObject {
                put("fileData", buildJsonObject {
                    part.mimeType?.let { put("mimeType", it) }
                    put("fileUri", part.fileUri)
                })
            })
        }
    }
}