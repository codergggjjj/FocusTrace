package com.focustrace.studyroom

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import com.focustrace.R
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private val Context.roomProfileDataStore by preferencesDataStore("study_room_profile")
private val nicknameKey = stringPreferencesKey("nickname")
private val avatarKey = stringPreferencesKey("avatar")
private const val MAX_SIDE = 128

internal class RoomProfileStore(private val context: Context) {
    val profile = context.roomProfileDataStore.data.map { prefs ->
        RoomProfile(prefs[nicknameKey] ?: RoomProfile().nickname, prefs[avatarKey].orEmpty())
    }

        .onStart {
            context.roomProfileDataStore.edit { prefs ->
                if (prefs[nicknameKey] == null) {
                    prefs[nicknameKey] = listOf("阿狸", "拉克丝", "亚索", "艾希", "凯特琳", "伊泽瑞尔", "锐雯", "霞", "洛", "格温").random()
                }
                if (prefs[avatarKey] == null) {
                    val portrait = listOf(R.raw.room_portrait_0, R.raw.room_portrait_1,
                        R.raw.room_portrait_2, R.raw.room_portrait_3).random()
                    prefs[avatarKey] = withContext(Dispatchers.IO) {
                        context.resources.openRawResource(portrait).use { Base64.encodeToString(it.readBytes(), Base64.NO_WRAP) }
                    }
                }
            }
        }

    suspend fun save(nickname: String, avatar: String) {
        val name = nickname.trim()
        require(name.isNotEmpty() && name.length <= NICKNAME_LIMIT && avatar.length <= AVATAR_LIMIT)
        context.roomProfileDataStore.edit { it[nicknameKey] = name; it[avatarKey] = avatar }
    }

    suspend fun import(uri: Uri): String = withContext(Dispatchers.IO) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val resolver = context.contentResolver
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        val side = maxOf(options.outWidth, options.outHeight)
        require(side in 1..65_536 && minOf(options.outWidth, options.outHeight) > 0)
        var sample = 1
        while (side / sample > MAX_SIDE * 2) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("无法读取图片")
        val ratio = (MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height)).coerceAtMost(1f)
        val scaled = Bitmap.createScaledBitmap(decoded, (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1), true)
        try {
            val output = ByteArrayOutputStream()
            check(scaled.compress(Bitmap.CompressFormat.JPEG, 80, output))
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP).also { require(it.length <= AVATAR_LIMIT) }
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }
}

internal fun decodeRoomAvatar(encoded: String): Bitmap? = runCatching {
    if (encoded.isEmpty() || encoded.length > AVATAR_LIMIT) return null
    val bytes = Base64.decode(encoded, Base64.NO_WRAP)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth !in 1..MAX_SIDE || bounds.outHeight !in 1..MAX_SIDE) return null
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()
