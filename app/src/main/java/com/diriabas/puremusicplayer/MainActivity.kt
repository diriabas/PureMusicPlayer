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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

private data class Track(val id: Long, val title: String, val artist: String, val uri: Uri)

class MainActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    private val requestPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermission.launch(permission)
        }
        setContent {
            val tracks = remember { mutableStateListOf<Track>().apply { addAll(loadTracks()) } }
            var playing by remember { mutableStateOf("") }
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFB6F36B), background = Color(0xFF101114), surface = Color(0xFF1B1D22)
            )) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF101114)) {
                    Column(Modifier.fillMaxSize().padding(20.dp)) {
                        Text("PURE MUSIC", style = MaterialTheme.typography.labelLarge, color = Color(0xFFB6F36B))
                        Spacer(Modifier.height(8.dp))
                        Text("Моя музыка", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                        Text("${tracks.size} аудиофайлов", color = Color.LightGray)
                        Spacer(Modifier.height(18.dp))
                        if (tracks.isEmpty()) {
                            Text("Музыка не найдена. Разрешите доступ к аудиофайлам.", color = Color.LightGray)
                        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(tracks, key = { it.id }) { track ->
                                Card(Modifier.fillMaxWidth().clickable {
                                    player?.setMediaItem(MediaItem.fromUri(track.uri)); player?.prepare(); player?.play(); playing = track.title
                                }, colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D22)), shape = RoundedCornerShape(14.dp)) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(track.title, color = Color.White, maxLines = 1)
                                            Text(track.artist, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(if (playing == track.title) "ИГРАЕТ" else "▶", color = Color(0xFFB6F36B))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text("Нажмите на композицию для воспроизведения", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    private fun loadTracks(): List<Track> {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST)
        val result = mutableListOf<Track>()
        contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                result += Track(id, c.getString(titleCol) ?: "Без названия", c.getString(artistCol) ?: "Неизвестный исполнитель", ContentUris.withAppendedId(collection, id))
            }
        }
        return result
    }

    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
}
