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
import androidx.compose.ui.graphics.toArgb
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

@Composable
private fun rememberMonogramArtwork(backgroundColor: Color): Bitmap? {
    val context = LocalContext.current
    return produceState<Bitmap?>(initialValue = null, key1 = context to backgroundColor) {
        value = withContext(Dispatchers.IO) {
            try {
                // Resolve by name so Kotlin does not depend on a generated R reference.
                val resourceId = context.resources.getIdentifier(
                    "av_monogram",
                    "drawable",
                    context.packageName
                )
                if (resourceId != 0) {
                    val original = BitmapFactory.decodeResource(context.resources, resourceId)
                    if (original == null) {
                        null
                    } else {
                        // Replace near-black backing pixels while preserving the gold artwork.
                        val recolored = original.copy(Bitmap.Config.ARGB_8888, true)
                        val replacementColor = backgroundColor.toArgb()
                        val replacementRed = android.graphics.Color.red(replacementColor)
                        val replacementGreen = android.graphics.Color.green(replacementColor)
                        val replacementBlue = android.graphics.Color.blue(replacementColor)

                        for (y in 0 until recolored.height) {
                            for (x in 0 until recolored.width) {
                                val pixel = recolored.getPixel(x, y)
                                val alpha = android.graphics.Color.alpha(pixel)
                                if (alpha == 0) continue

                                val red = android.graphics.Color.red(pixel)
                                val green = android.graphics.Color.green(pixel)
                                val blue = android.graphics.Color.blue(pixel)
                                val brightness = maxOf(red, green, blue)

                                if (brightness < 78) {
                                    recolored.setPixel(
                                        x, y,
                                        android.graphics.Color.argb(
                                            alpha, replacementRed, replacementGreen, replacementBlue
                                        )
                                    )
                                } else if (brightness < 112) {
                                    val blend = ((brightness - 78) / 34f).coerceIn(0f, 1f)
                                    val outRed = (replacementRed * (1f - blend) + red * blend).toInt()
                                    val outGreen = (replacementGreen * (1f - blend) + green * blend).toInt()
                                    val outBlue = (replacementBlue * (1f - blend) + blue * blend).toInt()
                                    recolored.setPixel(
                                        x, y,
                                        android.graphics.Color.argb(alpha, outRed, outGreen, outBlue)
                                    )
                                }
                            }
                        }
                        recolored
                    }
                } else {
                    null
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
    val monogram = rememberMonogramArtwork(themeSurface)

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
        } else if (monogram != null) {
            Image(
                bitmap = monogram.asImageBitmap(),
                contentDescription = "Золотой вензель AV",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
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

/** Compact 46 dp thumbnail used in track and album rows. */
@Composable
fun AlbumThumbnail(albumId: Long, themeSurface: Color = Color(0xFF25282C), modifier: Modifier = Modifier.size(46.dp)) {
    val bitmap = rememberAlbumArtwork(albumId)
    val monogram = rememberMonogramArtwork(themeSurface)

    Box(
        modifier = modifier
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
            monogram != null -> {
                Image(
                    bitmap = monogram.asImageBitmap(),
                    contentDescription = "Золотой круглый вензель AV",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            else -> {
                Text(
                    text = "AV",
                    color = CoverGold,
                    fontSize = 19.sp,
                    fontFamily = FontFamily.Cursive
                )
            }
        }
    }
}
