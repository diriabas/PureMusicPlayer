package com.diriabas.puremusicplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CoverGold = Color(0xFFD6B878)
private val CoverBlack = Color(0xFF0B0C0E)
private val CoverShape = RoundedCornerShape(20.dp)

@Composable
private fun rememberAlbumArtwork(albumId: Long): Bitmap? {
    val context = LocalContext.current
    return produceState<Bitmap?>(initialValue = null, key1 = albumId) {
        value = withContext(Dispatchers.IO) {
            try {
                val artworkUri = Uri.parse("content://media/external/audio/albumart/$albumId")
                context.contentResolver.openInputStream(artworkUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
                null
            }
        }
    }.value
}

/** Large square album cover with a fine champagne-gold frame. */
@Composable
fun AlbumCover(albumId: Long, modifier: Modifier = Modifier) {
    val bitmap = rememberAlbumArtwork(albumId)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CoverShape)
            .background(CoverBlack)
            .border(1.5.dp, CoverGold, CoverShape),
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
            Text(
                text = "AV",
                color = CoverGold.copy(alpha = 0.82f),
                fontSize = 112.sp,
                fontFamily = FontFamily.Cursive,
                fontWeight = FontWeight.Light
            )
        }
    }
}

/** Compact artwork tile for album and track lists. */
@Composable
fun AlbumThumbnail(albumId: Long) {
    val bitmap = rememberAlbumArtwork(albumId)
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(CoverBlack),
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
            Text("AV", color = CoverGold, fontSize = 19.sp, fontFamily = FontFamily.Cursive)
        }
    }
}
