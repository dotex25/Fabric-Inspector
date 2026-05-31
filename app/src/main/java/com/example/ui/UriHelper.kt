package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object UriHelper {
    fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri {
        val cacheDir = context.cacheDir
        val tempFile = File(cacheDir, "temp_fabric_" + System.currentTimeMillis() + ".jpg")
        FileOutputStream(tempFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
        return Uri.fromFile(tempFile)
    }
}
