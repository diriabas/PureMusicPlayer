
package com.diriabas.puremusicplayer

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay

private data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri
)

private val Accent = Color(0xFFB6F36B)
private val Background = Color(0xFF101114)
private val Panel = Color(0xFF1B1D22)

class MainActivity : ComponentActivity() {

    private var player: ExoPlayer? = null

    private val permission: String
        get() = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

    private val requestPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) recreate()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val exoPlayer = ExoPlayer.Builder(this).build()
        player = exoPlayer

        if (ContextCompat.checkSelfPermission(this, permission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission.launch(permission)
        }

        setContent {
            var tracks by remember {
                mutableStateOf(loadTracks())
            }

            var currentIndex by remember {
                mutableStateOf(-1)
            }

            var playing by remember {
                mutableStateOf(false)
            }

            var position by remember {
                mutableFloatStateOf(0f)
            }

            var duration by remember {
                mutableLongStateOf(0L)
            }

            val currentTrack = tracks.getOrNull(currentIndex)

            fun playAt(index: Int) {
                if (index !in tracks.indices) return

                currentIndex = index
                exoPlayer.setMediaItem(
                    MediaItem.fromUri(tracks[index].uri)
                )
                exoPlayer.prepare()
                exoPlayer.play()
                playing = true
            }

            LaunchedEffect(exoPlayer) {
                while (true) {
                    playing = exoPlayer.isPlaying
                    duration = exoPlayer.duration
                        .takeIf { it > 0 } ?: 0L

                    position = if (duration > 0) {
                        exoPlayer.currentPosition
                            .toFloat() / duration
                    } else 0f

                    delay(500)
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    background = Background,
                    surface = Panel
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                    ) {
                        Text(
                            "PURE MUSIC",
                            color = Accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(18.dp))

                        Text(
                            "Моя музыка",
                            fontSize = 29.sp,
                            color = Color.White
                        )

                        Text(
                            "${tracks.size} аудиофайлов",
                            color = Color.LightGray
                        )

                        Spacer(Modifier.height(18.dp))

                        if (currentTrack != null) {
                            Column(
                                horizontalAlignment =
                                    Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(190.dp)
                                        .background(
                                            Panel,
                                            RoundedCornerShape(22.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "♫",
                                        color = Accent,
                                        fontSize = 100.sp
                                    )
                                }

                                Spacer(Modifier.height(18.dp))

                                Text(
                                    currentTrack.title,
                                    color = Color.White,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    currentTrack.artist,
                                    color = Color.LightGray,
                                    fontSize = 15.sp
                                )

                                Spacer(Modifier.height(12.dp))

                                Slider(
                                    value = position.coerceIn(0f, 1f),
                                    onValueChange = {
                                        position = it
                                    },
                                    onValueChangeFinished = {
                                        if (duration > 0) {
                                            exoPlayer.seekTo(
                                                (position * duration).toLong()
                                            )
                                        }
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = Accent,
                                        activeTrackColor = Accent
                                    )
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        time(exoPlayer.currentPosition),
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        time(duration),
                                        color = Color.Gray,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.SpaceEvenly,
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {
                                    Text(
                                        "◀",
                                        color = Accent,
                                        fontSize = 27.sp,
                                        modifier = Modifier.clickable {
                                            playAt(currentIndex - 1)
                                        }
                                    )

                                    Text(
                                        if (playing) "Ⅱ" else "▶",
                                        color = Accent,
                                        fontSize = 42.sp,
                                        modifier = Modifier.clickable {
                                            if (exoPlayer.isPlaying) {
                                                exoPlayer.pause()
                                            } else {
                                                exoPlayer.play()
                                            }
                                            playing = exoPlayer.isPlaying
                                        }
                                    )

                                    Text(
                                        "▶",
                                        color = Accent,
                                        fontSize = 27.sp,
                                        modifier = Modifier.clickable {
                                            playAt(currentIndex + 1)
                                        }
                                    )
                                }
                            }

                            Spacer(Modifier.height(18.dp))
                            HorizontalDivider(color = Panel)
                            Spacer(Modifier.height(12.dp))
                        }

                        Text(
                            "ВСЕ КОМПОЗИЦИИ",
                            color = Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        if (tracks.isEmpty()) {
                            Text(
                                "Музыка не найдена. Проверьте разрешение на доступ к аудиофайлам.",
                                color = Color.LightGray
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement =
                                    Arrangement.spacedBy(7.dp)
                            ) {
                                itemsIndexed(
                                    tracks,
                                    key = { _, track -> track.id }
                                ) { index, track ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (index == currentIndex)
                                                    Color(0xFF293322)
                                                else Panel,
                                                RoundedCornerShape(13.dp)
                                            )
                                            .clickable {
                                                playAt(index)
                                            }
                                            .padding(14.dp),
                                        verticalAlignment =
                                            Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                track.title,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow =
                                                    TextOverflow.Ellipsis
                                            )
                                            Text(
                                                track.artist,
                                                color = Color.LightGray,
                                                fontSize = 12.sp
                                            )
                                        }

                                        Text(
                                            if (index == currentIndex &&
                                                playing) "Ⅱ" else "▶",
                                            color = Accent,
                                            fontSize = 19.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadTracks(): List<Track> {
        if (ContextCompat.checkSelfPermission(
                this, permission
            ) != PackageManager.PERMISSION_GRANTED
        ) return emptyList()

        val result = mutableListOf<Track>()

        val collection =
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST
        )

        contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC"
        )?.use { cursor ->

            val idColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media._ID
            )
            val titleColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.TITLE
            )
            val artistColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ARTIST
            )

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)

                result.add(
                    Track(
                        id = id,
                        title = cursor.getString(titleColumn)
                            ?: "Без названия",
                        artist = cursor.getString(artistColumn)
                            ?.takeUnless { it == "<unknown>" }
                            ?: "Неизвестный исполнитель",
                        uri = ContentUris.withAppendedId(
                            collection, id
                        )
                    )
                )
            }
        }

        return result
    }

    private fun time(milliseconds: Long): String {
        val seconds = (milliseconds / 1000).coerceAtLeast(0)
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }
}
