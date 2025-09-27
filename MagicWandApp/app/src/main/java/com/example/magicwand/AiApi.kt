package com.example.magicwand

import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

data class AiMessage(val role: String, val content: String)
data class AiRequest(
    val model: String,
    val messages: List<AiMessage>,
    val max_tokens: Int = 220,
    val temperature: Double = 0.7
)
data class AiChoice(val message: AiMessage)
data class AiResponse(val choices: List<AiChoice>)

interface AiApi {
    @Headers("Content-Type: application/json")
    @POST("v1/chat/completions")
    suspend fun getExplanation(@Body req: AiRequest): AiResponse
}
