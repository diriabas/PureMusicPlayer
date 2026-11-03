package com.diriabas.puremusicplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CoverPanel = Color(0xFF0B0C0E)

@Composable
fun AlbumCover(albumId: Long) {
    val context = LocalContext.current

    val bitmap = produceState<Bitmap?>(
        initialValue = null,
        key1 = albumId
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                val artworkUri = Uri.parse(
                    "content://media/external/audio/albumart/$albumId"
                )
                context.contentResolver.openInputStream(artworkUri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (_: Exception) {
                null
            }
        }
    }.value

    Box(
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(CoverPanel),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Обложка альбома",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.pure_music_monogram),
                contentDescription = "Золотой вензель Pure Music",
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
