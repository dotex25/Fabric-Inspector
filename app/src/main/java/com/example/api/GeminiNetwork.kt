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
            You are a highly precise, automated Industrial Vision Inspection System deployed on the production floor of a tier-1 garment manufacturer.
            
            Your task is to analyze the provided fabric image of a garment panel or finished piece, isolate any manufacturing discrepancies, and extract their spatial coordinate boundaries. You must ignore intentional fabric designs, print patterns (like polka dots or stripes), and normal texture shadows.
            
            Identify and locate all instances of garment and fabric defects. You must return a structured JSON array matching this Pydantic schema:
            [{
              "box_2d": [ymin, xmin, ymax, xmax], 
              "label": "Use EXACT label name from Target Directory below",
              "confidence": float (between 0.0 and 1.0)
            }]
            
            If no defects are present, return an empty array: []. Do not output any regular markdown text, conversational explanations, or formatting blocks. Return raw JSON only.
            
            ### Target Classification Directory:
            Scan the visual inputs specifically for the following categories:
            
            1. FABRIC_ANOMALIES:
               - "Hole / Tear" (Physical puncture or ripped yarns)
               - "Slub" (Defective thick bunching of yarn in weave)
               - "Contaminated Thread" (Foreign colored fibers trapped in fabric)
               - "Snag" (Pulled loops on the surface)
               
            2. SEWING_DEFECTS:
               - "Skipped Stitch" (Missing thread loops along a seam line)
               - "Open Seam" (Broken stitches exposing a structural gap between panels)
               - "Seam Puckering" (Taut, bunched, or wrinkled stitch lines)
               - "Uneven Stitching" (Wandering, crooked, or misaligned needle paths)
               - "Wavy Seam" (Stretched out, rippled edges)
               
            3. FINISHING_DEFECTS:
               - "Oil Spot" (Machine lubricant stains or dark grease drops)
               - "Dye Stain" (Color bleeding, shading variation, or chemical smudges)
               - "Uncut Thread" (Dangling loose thread tails at tails/hems)
               - "Iron Burn" (Shiny glaze or scorched yellowing from pressing)
               - "Tailor Mark" (Leftover chalk lines or pattern ink pen marks)
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
            
            Reference this dataset to systematically match user expectations. If you see similar patterns as described above, apply the corresponding defect labels accurately.
        """.trimIndent()
    }

    // Call the Gemini API to inspect fabric
    suspend fun inspectFabric(
        bitmap: Bitmap,
        pastCorrections: List<com.example.data.DefectBox>,
        customApiKey: String? = null,
        customModel: String? = null
    ): List<DefectApiResponse> {
        val apiKey = if (!customApiKey.isNullOrBlank()) customApiKey else BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException("API key is unconfigured. Please configure your API key in the Settings panel of the app or in the Secrets panel of AI Studio.")
        }

        val prompt = "Locate and label all garment or fabric defects in this sample. Return the bounding box coordinates [ymin, xmin, ymax, xmax], category label name, and your confidence score for each defect as raw JSON."

        // Build Schema for JSON Response format:
        val schema = ResponseSchema(
            type = "ARRAY",
            description = "List of all detected garment/fabric defect bounding boxes and categories.",
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
                        description = "Exact defect category name from: 'Hole / Tear', 'Slub', 'Contaminated Thread', 'Snag', 'Skipped Stitch', 'Open Seam', 'Seam Puckering', 'Uneven Stitching', 'Wavy Seam', 'Oil Spot', 'Dye Stain', 'Uncut Thread', 'Iron Burn', 'Tailor Mark'"
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

        val targetModel = if (!customModel.isNullOrBlank()) customModel else "gemini-2.5-flash"
        val response = RetrofitClient.service.generateContent(
            model = targetModel,
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

    // Direct dynamic validation of custom keys and model types
    suspend fun testApiKeyAndModel(apiKey: String, model: String): String {
        if (apiKey.isBlank()) {
            return "ERROR: Custom API Key is empty."
        }
        val testRequest = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = "Say active")))
            ),
            generationConfig = GenerationConfig(
                temperature = 0.1f
            )
        )
        return try {
            val response = RetrofitClient.service.generateContent(
                model = model,
                apiKey = apiKey,
                request = testRequest
            )
            val reply = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (!reply.isNullOrBlank()) {
                "SUCCESS: Model '$model' is responsive & reachable!"
            } else {
                "SUCCESS: Model standard latency check passed."
            }
        } catch (e: retrofit2.HttpException) {
            val code = e.code()
            val errorBody = e.response()?.errorBody()?.string() ?: ""
            if (code == 429) {
                "LIMIT EXCEEDED (429): Selected model or key is rate limited. Choose another tier or key."
            } else if (code == 400 && errorBody.contains("API_KEY_INVALID", ignoreCase = true)) {
                "AUTH ERROR (400): Key authentication signature failed. Correct the text pattern."
            } else if (code == 503) {
                "SERVICE TEMPORARILY DOWN (503): Model backend overloaded. Try 'gemini-1.5-flashfallback'."
            } else {
                "CONNECT ERROR ($code): ${e.message() ?: "HTTP error"} - ${if (errorBody.length > 80) errorBody.take(80) + "..." else errorBody}"
            }
        } catch (e: Exception) {
            "NETWORK FAILURE: ${e.localizedMessage ?: "Unknown connectivity error"}"
        }
    }
}
