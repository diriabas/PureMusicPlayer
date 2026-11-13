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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
                val artworkUri = Uri.parse(
                    "content://media/external/audio/albumart/$albumId"
                )
                context.contentResolver.openInputStream(artworkUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } catch (_: Exception) {
                null
            }
        }
    }.value
}

/** Large square cover used on the player screen and album cards. */
@Composable
fun AlbumCover(
    albumId: Long,
    modifier: Modifier = Modifier,
    themeSurface: Color = Color(0xFF25282C)
) {
    val bitmap = rememberAlbumArtwork(albumId)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CoverShape)
            .background(themeSurface)
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
            Image(
                painter = painterResource(id = R.drawable.av_monogram_transparent),
                contentDescription = "Золотой декоративный вензель",
                modifier = Modifier.fillMaxSize().padding(12.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

/** Compact 46 dp thumbnail used in track and album rows. */
@Composable
fun AlbumThumbnail(albumId: Long, themeSurface: Color = Color(0xFF25282C)) {
    val bitmap = rememberAlbumArtwork(albumId)

    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(themeSurface),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Обложка альбома",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Image(
                    painter = painterResource(id = R.drawable.av_monogram_transparent),
                    contentDescription = "Золотой декоративный вензель",
                    modifier = Modifier.fillMaxSize().padding(3.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
