package com.focustrace.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class FocusBackgroundImageStore(private val context: Context) {
    private fun ownedFile(path: String?): File? {
        if (path == null) return null
        val file = File(path)
        return file.takeIf { it.parentFile?.canonicalFile == context.filesDir.canonicalFile &&
            it.name.startsWith("focus_background_") && it.name.endsWith(".jpg") }
    }

    suspend fun readForBackup(path: String?): ByteArray? = withContext(Dispatchers.IO) {
        ownedFile(path)?.takeIf { it.isFile }?.also {
            require(it.length() in 1..8_000_000) { "自定义背景图片过大" }
        }?.readBytes()
    }

    suspend fun restoreFromBackup(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        require(bytes.size in 1..8_000_000) { "备份图片大小无效" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 1..1920 && bounds.outHeight in 1..1920) { "备份图片格式或尺寸无效" }
        val file = File.createTempFile("focus_background_", ".jpg", context.filesDir)
        try { file.writeBytes(bytes); file.absolutePath }
        catch (e: Exception) { file.delete(); throw e }
    }

    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (resolver.openInputStream(uri) ?: error("无法读取图片")).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片格式不受支持" }
        var sample = 1
        while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1920) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("无法读取图片")
        val scale = minOf(1f, 1920f / decoded.width, 1920f / decoded.height)
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1), true) else decoded
        val file = File.createTempFile("focus_background_", ".jpg", context.filesDir)
        try {
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it)) }
            file.absolutePath
        } catch (e: Exception) {
            file.delete()
            throw e
        } finally {
            if (bitmap !== decoded) bitmap.recycle()
            decoded.recycle()
        }
    }

    suspend fun deleteOwned(path: String?) = withContext(Dispatchers.IO) {
        ownedFile(path)?.delete()
    }
}
