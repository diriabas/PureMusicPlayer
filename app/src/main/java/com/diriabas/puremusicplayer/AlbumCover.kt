
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CoverGold = Color(0xFFD6B878)
private val CoverGoldLight = Color(0xFFF0DDAA)
private val CoverBlack = Color(0xFF0B0C0E)
private val CoverShape = RoundedCornerShape(22.dp)
private val ThumbnailShape = RoundedCornerShape(10.dp)

@Composable
private fun rememberAlbumArtwork(albumId: Long): Bitmap? {
    val context = LocalContext.current

    return produceState<Bitmap?>(
        initialValue = null,
        key1 = albumId
    ) {
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

private fun premiumGoldBorder() = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.78f),
        CoverGoldLight,
        CoverGold.copy(alpha = 0.48f),
        Color.White.copy(alpha = 0.24f)
    )
)

/**
 * Large square cover used on the player screen and album cards.
 */
@Composable
fun AlbumCover(
    albumId: Long,
    modifier: Modifier = Modifier,
    themeSurface: Color = Color(0xFF25282C),
    isGlassTheme: Boolean = false
) {
    val bitmap = rememberAlbumArtwork(albumId)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .shadow(20.dp, CoverShape)
            .clip(CoverShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        themeSurface,
                        CoverBlack,
                        themeSurface.copy(alpha = 0.92f)
                    )
                )
            )
            .border(
                1.6.dp,
                premiumGoldBorder(),
                CoverShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Обложка альбома",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            // A restrained lower vignette gives the artwork depth.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.04f),
                                Color.Black.copy(alpha = 0.25f)
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = if (isGlassTheme) {
                                // Slightly lighter monogram background
                                // only in the Glass theme.
                                listOf(
                                    Color(0xFF424954),
                                    Color(0xFF20262F),
                                    Color(0xFF111419)
                                )
                            } else {
                                listOf(
                                    Color(0xFF39404A),
                                    Color(0xFF171B21),
                                    CoverBlack
                                )
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(
                        id = R.drawable.av_monogram_transparent
                    ),
                    contentDescription = "Золотой декоративный вензель",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

/**
 * Compact premium thumbnail used in track and album rows.
 */
@Composable
fun AlbumThumbnail(
    albumId: Long,
    themeSurface: Color = Color(0xFF25282C)
) {
    val bitmap = rememberAlbumArtwork(albumId)

    Box(
        modifier = Modifier
            .size(48.dp)
            .shadow(5.dp, ThumbnailShape)
            .clip(ThumbnailShape)
            .background(themeSurface)
            .border(
                0.8.dp,
                premiumGoldBorder(),
                ThumbnailShape
            ),
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF343B45),
                                CoverBlack
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(
                        id = R.drawable.av_monogram_transparent
                    ),
                    contentDescription = "Золотой декоративный вензель",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
