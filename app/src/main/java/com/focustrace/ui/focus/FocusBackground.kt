package com.focustrace.ui.focus

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.focustrace.R
import com.focustrace.data.datastore.FocusBackgrounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun FocusBackground(id: String, modifier: Modifier = Modifier) {
    val custom by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, id) {
        value = if (id !in FocusBackgrounds.ids) withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(id)?.asImageBitmap()
        } else null
    }
    val fallback = when (id) {
        FocusBackgrounds.FOREST -> R.drawable.focus_bg_forest
        FocusBackgrounds.SEASIDE -> R.drawable.focus_bg_seaside
        else -> R.drawable.focus_bg_lake
    }
    val painter: Painter = custom?.let(::BitmapPainter) ?: painterResource(fallback)
    Box(modifier) {
        Image(painter, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f)))
    }
}
