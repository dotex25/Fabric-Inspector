package com.example.api

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()
    }

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    fun getMoshi(): Moshi = moshi
}

object GeminiScanner {
    
    // Convert bitmap to base64
    fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    // Build the dynamic instruction text showing operators past corrections as a few-shot log
    private fun buildSystemInstruction(pastCorrections: List<com.example.data.DefectBox>): String {
        val baseInstructions = """
            You are an expert quality control vision AI assistant on a garment factory inspection line.
            Your task is to analyze the provided fabric image and detect anomalies or defects.
            
            Identify and locate all instances of fabric defects. For each defect, you must return:
            1. 'box_2d': Coordinates indicating [ymin, xmin, ymax, xmax] normalized on a 0 to 1000 scale representing the bounding box.
            2. 'label': The exact category of defect detected. You MUST use one of these categories:
               - 'Hole' (punctures, tears, or broken knit structures)
               - 'Oil Spot' (dark oily droplets, grease patterns or marks)
               - 'Stain' (faded areas, paint, dirt spots or dye discolorations)
               - 'Torn Thread' (loose single threads, fraying, pulled stitches or loose yarn loops)
            3. 'confidence': A floating point score of confidence (0.0 to 1.0).
            
            Always provide a strict, well-formed bounding box. Do not highlight normal fabric patterns or minor weave texture variations.
        """.trimIndent()

        if (pastCorrections.isEmpty()) {
            return baseInstructions
        }

        // Add feedback-loop few-shot memory context from Room!
        val correctionsLog = pastCorrections.take(15).joinToString("\n") { box ->
            " - Verified Defect Category: '${box.label}' detected on a fabric sample with normalized bounds: [ymin=${box.yMin}, xmin=${box.xMin}, ymax=${box.yMax}, xmax=${box.xMax}] (Confidence: ${box.confidence})"
        }

        return """
            $baseInstructions

            === HUMAN OPERATOR FEEDBACK MEMORY & FEW-SHOT CORRECTIONS ===
            Below is a dynamic history of human-supervised corrections on this production line. Use these historical examples of bounding box alignments and corrected defect classifications to guide your current factory inspection:
            $correctionsLog
            
            Reference this dataset to systematic match user expectations. If you see similar patterns as described above, apply the corresponding defect labels accurately.
        """.trimIndent()
    }

    // Call the Gemini API to inspect fabric
    suspend fun inspectFabric(
        bitmap: Bitmap,
        pastCorrections: List<com.example.data.DefectBox>
    ): List<DefectApiResponse> {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API key is unconfigured. Please configure your API key in the Secrets panel of AI Studio.")
        }

        val prompt = "Locate and label all fabric defects in this fabric sample. Return the bounding box coordinates, label name, and your confidence score for each defect."

        // Build Schema for JSON Response format:
        val schema = ResponseSchema(
            type = "ARRAY",
            description = "List of all detected fabric defect bounding boxes and categories.",
            items = ResponseSchema(
                type = "OBJECT",
                properties = mapOf(
                    "box_2d" to ResponseSchema(
                        type = "ARRAY",
                        description = "Normalized coordinates representation [ymin, xmin, ymax, xmax] in the range of 0 to 1000.",
                        items = ResponseSchema(type = "INTEGER")
                    ),
                    "label" to ResponseSchema(
                        type = "STRING",
                        description = "Exact defect name: 'Hole', 'Oil Spot', 'Stain', or 'Torn Thread'"
                    ),
                    "confidence" to ResponseSchema(
                        type = "NUMBER",
                        description = "Vision model confidence score from 0.0 to 1.0"
                    )
                ),
                required = listOf("box_2d", "label", "confidence")
            )
        )

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = bitmap.toBase64()))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                responseSchema = schema,
                temperature = 0.1f
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = buildSystemInstruction(pastCorrections)))
            )
        )

        // Using "gemini-3.5-flash" as the default model
        val response = RetrofitClient.service.generateContent(
            model = "gemini-3.5-flash",
            apiKey = apiKey,
            request = request
        )

        val responseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Received an empty response from vision model")

        // Parse list response using Moshi
        val listType = Types.newParameterizedType(List::class.java, DefectApiResponse::class.java)
        val adapter = RetrofitClient.getMoshi().adapter<List<DefectApiResponse>>(listType)
        
        return adapter.fromJson(responseText) ?: emptyList()
    }
}
