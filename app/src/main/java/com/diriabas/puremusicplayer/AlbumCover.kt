package com.diriabas.puremusicplayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

private val CoverGold = Color(0xFFD6A94F)
private val CoverBlack = Color(0xFF080807)

/** Full-width premium AV monogram artwork used on the Now Playing screen. */
@Composable
fun AlbumCover(albumId: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(330.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF171006), CoverBlack, Color(0xFF100B04))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Flowing champagne-gold light ribbons behind the monogram.
            for (ribbon in 0..3) {
                val baseY = h * (0.34f + ribbon * 0.16f)
                val points = 90
                val path = androidx.compose.ui.graphics.Path()
                for (i in 0..points) {
                    val x = w * i / points
                    val wave = sin(i * 0.075f + ribbon * 1.15f) * h * 0.055f
                    val y = baseY + wave
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = CoverGold.copy(alpha = if (ribbon == 1) 0.46f else 0.17f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (ribbon == 1) 2.2.dp.toPx() else 1.dp.toPx())
                )
            }
            // Subtle warm glow in the center.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CoverGold.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.53f),
                    radius = h * 0.62f
                ),
                radius = h * 0.62f,
                center = Offset(w * 0.5f, h * 0.53f)
            )
        }
        Text(
            text = "AV",
            color = CoverGold.copy(alpha = 0.22f),
            fontSize = 220.sp,
            fontFamily = FontFamily.Cursive,
            fontWeight = FontWeight.Light,
            letterSpacing = (-13).sp,
            maxLines = 1
        )
    }
}

/** Small artwork used in album and track lists. */
@Composable
fun AlbumThumbnail(albumId: Long) {
    Box(
        modifier = Modifier.size(46.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "AV",
            color = CoverGold.copy(alpha = 0.75f),
            fontSize = 19.sp,
            fontFamily = FontFamily.Cursive
        )
    }
}
