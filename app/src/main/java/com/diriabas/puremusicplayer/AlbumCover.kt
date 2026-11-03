package com.diriabas.puremusicplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CoverAccent = Color(0xFFD6B878)
private val CoverPanel = Color(0xFF0B0C0E)

@Composable
private fun loadAlbumArtwork(albumId: Long): Bitmap? {
    val context = LocalContext.current
    return produceState<Bitmap?>(initialValue = null, key1 = albumId) {
        value = withContext(Dispatchers.IO) {
            try {
                val artworkUri = Uri.parse("content://media/external/audio/albumart/$albumId")
                context.contentResolver.openInputStream(artworkUri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (_: Exception) {
                null
            }
        }
    }.value
}

/** Full-width artwork for the Now Playing screen. */
@Composable
fun AlbumCover(albumId: Long) {
    val bitmap = loadAlbumArtwork(albumId)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(CoverPanel),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Обложка альбома",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(text = "♫", color = CoverAccent, fontSize = 100.sp)
        }
    }
}

/** Small artwork used in album and track lists. */
@Composable
fun AlbumThumbnail(albumId: Long) {
    val bitmap = loadAlbumArtwork(albumId)
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CoverPanel),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Обложка альбома",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(text = "♫", color = CoverAccent, fontSize = 25.sp)
        }
    }
}
