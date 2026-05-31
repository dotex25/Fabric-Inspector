package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

enum class FabricType(val displayName: String, val filename: String) {
    COTTON_CLEAN("Clean Cream Cotton", "cotton_clean"),
    DENIM_OIL_SPOT("Indigo Denim w/ Oil Spot", "denim_oil"),
    LINEN_TORN_THREAD("Check Linen w/ Torn Thread", "linen_torn"),
    SILK_STAIN("Satin Silk w/ Red Dye Stain", "silk_stain")
}

object SampleFabricGenerator {

    fun generateFabric(type: FabricType): Bitmap {
        val width = 800
        val height = 800
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        when (type) {
            FabricType.COTTON_CLEAN -> {
                // Background
                canvas.drawColor(Color.parseColor("#FFFDF0")) // Warm off-white
                
                // Draw fine cotton weave texture lines
                paint.color = Color.parseColor("#EFECE0")
                paint.strokeWidth = 2f
                for (i in 0..width step 16) {
                    canvas.drawLine(i.toFloat(), 0f, i.toFloat(), height.toFloat(), paint)
                    canvas.drawLine(0f, i.toFloat(), width.toFloat(), i.toFloat(), paint)
                }
            }
            FabricType.DENIM_OIL_SPOT -> {
                // Indigo Background
                canvas.drawColor(Color.parseColor("#2E5077"))
                
                // Draw denim twill diagonal ridges
                paint.color = Color.parseColor("#243D5C")
                paint.strokeWidth = 3f
                for (i in -width..width step 20) {
                    canvas.drawLine(i.toFloat(), 0f, (i + height).toFloat(), height.toFloat(), paint)
                }

                // Smooth highlight ridges
                paint.color = Color.parseColor("#446B96")
                paint.strokeWidth = 1f
                for (i in -width..width step 20) {
                    canvas.drawLine((i + 10).toFloat(), 0f, (i + 10 + height).toFloat(), height.toFloat(), paint)
                }

                // Draw Anomaly: OIL SPOT (Dark organic blob)
                val spotPaint = Paint().apply {
                    color = Color.parseColor("#121B26") // Deep charcoal ink
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                // Center at X: 450, Y: 350
                canvas.drawCircle(450f, 350f, 40f, spotPaint)
                canvas.drawCircle(430f, 370f, 25f, spotPaint) // secondary drip
                canvas.drawCircle(470f, 340f, 20f, spotPaint) // tertiary splash
            }
            FabricType.LINEN_TORN_THREAD -> {
                // Beige background
                canvas.drawColor(Color.parseColor("#E3CAA5"))
                
                // Draw rustic linen grid pattern
                paint.color = Color.parseColor("#D3B895")
                paint.strokeWidth = 4f
                for (i in 0..width step 40) {
                    canvas.drawLine(i.toFloat(), 0f, i.toFloat(), height.toFloat(), paint)
                    canvas.drawLine(0f, i.toFloat(), width.toFloat(), i.toFloat(), paint)
                }

                // Thread highlights
                paint.color = Color.parseColor("#FFF2CC")
                paint.strokeWidth = 1.5f
                for (i in 0..width step 40) {
                    canvas.drawLine((i + 10).toFloat(), 0f, (i + 10).toFloat(), height.toFloat(), paint)
                    canvas.drawLine(0f, (i + 10).toFloat(), width.toFloat(), (i + 10).toFloat(), paint)
                }

                // Draw Anomaly: TORN THREAD (Fraying fibers and loose stitch thread)
                val threadPaint = Paint().apply {
                    color = Color.parseColor("#F5F5F5") // Bright white frayed threads
                    strokeWidth = 3f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                
                // Draw zig zag messy torn lines around X: 250, Y: 600
                val path = Path().apply {
                    moveTo(200f, 580f)
                    lineTo(240f, 620f)
                    lineTo(220f, 650f)
                    moveTo(215f, 610f)
                    lineTo(270f, 570f)
                    lineTo(290f, 630f)
                }
                canvas.drawPath(path, threadPaint)
                
                // Draw a little hole background underneath
                val gapPaint = Paint().apply {
                    color = Color.parseColor("#806443") // Deep shadow hole
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(235f, 610f, 18f, gapPaint)
            }
            FabricType.SILK_STAIN -> {
                // Silk royal purple color
                canvas.drawColor(Color.parseColor("#7A1CAC"))
                
                // Draw glossy silk folds using a gradient effect
                paint.color = Color.parseColor("#9138B8")
                paint.strokeWidth = 80f
                paint.style = Paint.Style.STROKE
                paint.isAntiAlias = true
                canvas.drawLine(-100f, 400f, 900f, 200f, paint)
                canvas.drawLine(-100f, 600f, 900f, 450f, paint)

                paint.color = Color.parseColor("#5F108C")
                canvas.drawLine(-100f, 200f, 900f, 50f, paint)

                // Draw Anomaly: RED DYE STAIN (Spilled dye spot)
                val stainPaint = Paint().apply {
                    color = Color.parseColor("#40B43F3F") // Semi-transparent crimson stain
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                // Draw some irregular overlapping circles simulating a splash
                canvas.drawCircle(320f, 250f, 45f, stainPaint)
                canvas.drawCircle(350f, 270f, 35f, stainPaint)
                canvas.drawCircle(300f, 280f, 30f, stainPaint)
            }
        }

        return bitmap
    }
}
