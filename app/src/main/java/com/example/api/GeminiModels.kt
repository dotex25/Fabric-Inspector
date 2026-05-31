package com.example.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String // Bas64 encoded
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = "application/json",
    val responseSchema: ResponseSchema? = null,
    val temperature: Float? = 0.1f
)

@JsonClass(generateAdapter = true)
data class ResponseSchema(
    val type: String, // "OBJECT", "ARRAY", "STRING", "INTEGER", "NUMBER"
    val description: String? = null,
    val properties: Map<String, ResponseSchema>? = null,
    val required: List<String>? = null,
    val items: ResponseSchema? = null
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

@JsonClass(generateAdapter = true)
data class DefectApiResponse(
    @Json(name = "box_2d") val box2d: List<Int>, // [ymin, xmin, ymax, xmax]
    val label: String,
    val confidence: Float
)
