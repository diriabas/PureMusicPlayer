
package com.diriabas.puremusicplayer

import android.Manifest
import android.content.ContentUris
import android.content.ComponentName
import org.json.JSONArray
import org.json.JSONObject
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay

private data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    val albumId: Long
)

private val Accent = Color(0xFFB6F36B)
private val Background = Color(0xFF101114)
private val Panel = Color(0xFF1B1D22)

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controllerState = mutableStateOf<MediaController?>(null)

    private val permission: String
        get() = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    private val requestPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotificationPermission.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                } else {
                    recreate()
                }
            }
        }

    private val requestNotificationPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            // Permission result is applied immediately.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sessionToken = SessionToken(
            this,
            ComponentName(this, PlaybackService::class.java)
        )

        val future = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener({
            try {
                controllerState.value = future.get()
            } catch (error: Exception) {
                controllerState.value = null
            }
        }, ContextCompat.getMainExecutor(this))

        if (ContextCompat.checkSelfPermission(this, permission) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission.launch(permission)
        } else if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        setContent {
            val exoPlayer = controllerState.value

            if (exoPlayer == null) {
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
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Подключение аудиоплеера…",
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                var tracks by remember { mutableStateOf(loadTracks()) }
                var currentIndex by remember { mutableIntStateOf(-1) }
                var playing by remember { mutableStateOf(false) }
                var position by remember { mutableFloatStateOf(0f) }
                var duration by remember { mutableLongStateOf(0L) }
                var playlists by remember { mutableStateOf(loadPlaylists()) }
                var selectedPlaylist by remember { mutableStateOf<String?>(null) }
                var sortMode by remember { mutableStateOf("По названию") }
                var sortMenuExpanded by remember { mutableStateOf(false) }
                var playlistMenuExpanded by remember { mutableStateOf(false) }
                var pendingTrack by remember { mutableStateOf<Track?>(null) }
                var newPlaylistName by remember { mutableStateOf("") }
                var searchQuery by remember { mutableStateOf("") }
                var favoritesOnly by remember { mutableStateOf(false) }
                var favoriteIds by remember { mutableStateOf(loadFavorites()) }

                val visibleTracks = remember(
                    tracks, playlists, selectedPlaylist, sortMode,
                    searchQuery, favoritesOnly, favoriteIds
                ) {
                    val base = if (selectedPlaylist == null) tracks else {
                        val ids = playlists[selectedPlaylist].orEmpty().toSet()
                        tracks.filter { it.id in ids }
                    }

                    val filtered = base.filter { track ->
                        val matchesSearch = searchQuery.isBlank() ||
                            track.title.contains(searchQuery, ignoreCase = true) ||
                            track.artist.contains(searchQuery, ignoreCase = true)

                        val matchesFavorites = !favoritesOnly ||
                            track.id in favoriteIds

                        matchesSearch && matchesFavorites
                    }

                    when (sortMode) {
                        "По исполнителю" -> filtered.sortedWith(
                            compareBy<Track> { it.artist.lowercase() }
                                .thenBy { it.title.lowercase() }
                        )
                        else -> filtered.sortedBy { it.title.lowercase() }
                    }
                }

                val currentTrack = tracks.getOrNull(currentIndex)

                fun playAt(index: Int) {
                    if (index !in tracks.indices) return

                    currentIndex = index

                    val mediaItems = tracks.map { track ->
                        val metadata = MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setArtworkUri(
                                Uri.parse(
                                    "content://media/external/audio/albumart/${track.albumId}"
                                )
                            )
                            .build()

                        MediaItem.Builder()
                            .setUri(track.uri)
                            .setMediaMetadata(metadata)
                            .build()
                    }

                    exoPlayer.setMediaItems(mediaItems, index, 0L)
                    exoPlayer.prepare()
                    exoPlayer.play()
                    playing = true
                }

                LaunchedEffect(exoPlayer) {
                    while (true) {
                        val serviceIndex = exoPlayer.currentMediaItemIndex

                        if (serviceIndex >= 0 && serviceIndex in tracks.indices) {
                            currentIndex = serviceIndex
                        }

                        playing = exoPlayer.isPlaying
                        duration = exoPlayer.duration.takeIf { it > 0 } ?: 0L

                        position = if (duration > 0L) {
                            exoPlayer.currentPosition.toFloat() / duration
                        } else {
                            0f
                        }

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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "PURE MUSIC",
                                    color = Accent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "Ванови Антон",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Spacer(Modifier.height(18.dp))

                            Text(
                                text = "Моя музыка",
                                fontSize = 29.sp,
                                color = Color.White
                            )

                            Text(
                                text = "${tracks.size} аудиофайлов",
                                color = Color.LightGray
                            )

                            Spacer(Modifier.height(18.dp))

                            if (currentTrack != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AlbumCover(albumId = currentTrack.albumId)

                                    Spacer(Modifier.height(18.dp))

                                    Text(
                                        text = currentTrack.title,
                                        color = Color.White,
                                        fontSize = 23.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = currentTrack.artist,
                                        color = Color.LightGray,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(Modifier.height(12.dp))

                                    Slider(
                                        value = position.coerceIn(0f, 1f),
                                        onValueChange = { position = it },
                                        onValueChangeFinished = {
                                            if (duration > 0L) {
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
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = time(exoPlayer.currentPosition),
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )

                                        Text(
                                            text = time(duration),
                                            color = Color.Gray,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "◀",
                                            color = Accent,
                                            fontSize = 27.sp,
                                            modifier = Modifier.clickable {
                                                playAt(currentIndex - 1)
                                            }
                                        )

                                        Text(
                                            text = if (playing) "Ⅱ" else "▶",
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
                                            text = "▶",
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (selectedPlaylist == null) {
                                        "ВСЕ КОМПОЗИЦИИ"
                                    } else {
                                        selectedPlaylist!!.uppercase()
                                    },
                                    color = Accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )

                                Box {
                                    Text(
                                        text = "СОРТИРОВКА ▾",
                                        color = Color.LightGray,
                                        fontSize = 11.sp,
                                        modifier = Modifier.clickable {
                                            sortMenuExpanded = true
                                        }
                                    )

                                    DropdownMenu(
                                        expanded = sortMenuExpanded,
                                        onDismissRequest = {
                                            sortMenuExpanded = false
                                        }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("По названию") },
                                            onClick = {
                                                sortMode = "По названию"
                                                sortMenuExpanded = false
                                            }
                                        )

                                        DropdownMenuItem(
                                            text = { Text("По исполнителю") },
                                            onClick = {
                                                sortMode = "По исполнителю"
                                                sortMenuExpanded = false
                                            }
                                        )
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Box {
                                    Text(
                                        text = "СПИСОК ▾",
                                        color = Accent,
                                        fontSize = 11.sp,
                                        modifier = Modifier.clickable {
                                            playlistMenuExpanded = true
                                        }
                                    )

                                    DropdownMenu(
                                        expanded = playlistMenuExpanded,
                                        onDismissRequest = {
                                            playlistMenuExpanded = false
                                        }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Все композиции") },
                                            onClick = {
                                                selectedPlaylist = null
                                                playlistMenuExpanded = false
                                            }
                                        )

                                        playlists.keys.sorted().forEach { name ->
                                            DropdownMenuItem(
                                                text = { Text(name) },
                                                onClick = {
                                                    selectedPlaylist = name
                                                    playlistMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = {
                                    Text("Поиск по названию или исполнителю")
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        Text(
                                            text = "✕",
                                            color = Color.LightGray,
                                            modifier = Modifier
                                                .clickable { searchQuery = "" }
                                                .padding(8.dp)
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Accent,
                                    unfocusedBorderColor = Panel,
                                    focusedLabelColor = Accent,
                                    unfocusedLabelColor = Color.LightGray,
                                    cursorColor = Accent
                                )
                            )

                            Text(
                                text = if (favoritesOnly) {
                                    "★ ПОКАЗАТЬ ВСЕ ПЕСНИ"
                                } else {
                                    "☆ ТОЛЬКО ИЗБРАННОЕ"
                                },
                                color = Accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        favoritesOnly = !favoritesOnly
                                    }
                                    .padding(vertical = 8.dp)
                            )

                            if (visibleTracks.isEmpty()) {
                                Text(
                                    text = if (selectedPlaylist == null) {
                                        "Музыка не найдена. Проверьте разрешение на доступ к аудиофайлам."
                                    } else {
                                        "В этом плейлисте пока нет песен."
                                    },
                                    color = Color.LightGray
                                )
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    itemsIndexed(
                                        items = visibleTracks,
                                        key = { _, track -> track.id }
                                    ) { _, track ->
                                        val trackIndex =
                                            tracks.indexOfFirst { it.id == track.id }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    color = if (track.id == currentTrack?.id) {
                                                        Color(0xFF293322)
                                                    } else {
                                                        Panel
                                                    },
                                                    shape = RoundedCornerShape(13.dp)
                                                )
                                                .clickable {
                                                    playAt(trackIndex)
                                                }
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AlbumThumbnail(albumId = track.albumId)

                                            Spacer(Modifier.width(12.dp))

                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = track.title,
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Text(
                                                    text = track.artist,
                                                    color = Color.LightGray,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Text(
                                                text = "＋",
                                                color = Accent,
                                                fontSize = 23.sp,
                                                modifier = Modifier
                                                    .clickable {
                                                        pendingTrack = track
                                                    }
                                                    .padding(
                                                        horizontal = 8.dp,
                                                        vertical = 4.dp
                                                    )
                                            )

                                            Text(
                                                text = if (track.id in favoriteIds) "★" else "☆",
                                                color = Accent,
                                                fontSize = 21.sp,
                                                modifier = Modifier
                                                    .clickable {
                                                        val updated =
                                                            favoriteIds.toMutableSet()

                                                        if (!updated.add(track.id)) {
                                                            updated.remove(track.id)
                                                        }

                                                        favoriteIds = updated
                                                        saveFavorites(updated)
                                                    }
                                                    .padding(
                                                        horizontal = 7.dp,
                                                        vertical = 4.dp
                                                    )
                                            )

                                            Text(
                                                text = if (
                                                    track.id == currentTrack?.id && playing
                                                ) "Ⅱ" else "▶",
                                                color = Accent,
                                                fontSize = 19.sp
                                            )
                                        }
                                    }
                                }
                            }

                            if (pendingTrack != null) {
                                AlertDialog(
                                    onDismissRequest = {
                                        pendingTrack = null
                                        newPlaylistName = ""
                                    },
                                    title = {
                                        Text("Добавить в плейлист")
                                    },
                                    text = {
                                        Column {
                                            Text(
                                                text = pendingTrack?.title.orEmpty(),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(Modifier.height(8.dp))

                                            playlists.keys.sorted().forEach { name ->
                                                TextButton(
                                                    onClick = {
                                                        val track = pendingTrack

                                                        if (track != null) {
                                                            val updated =
                                                                playlists.toMutableMap()

                                                            updated[name] =
                                                                (updated[name].orEmpty() +
                                                                    track.id).distinct()

                                                            playlists = updated
                                                            savePlaylists(updated)
                                                        }

                                                        pendingTrack = null
                                                    }
                                                ) {
                                                    Text("＋ $name")
                                                }
                                            }

                                            OutlinedTextField(
                                                value = newPlaylistName,
                                                onValueChange = {
                                                    newPlaylistName = it
                                                },
                                                label = {
                                                    Text("Новый плейлист")
                                                },
                                                singleLine = true
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                val name = newPlaylistName.trim()
                                                val track = pendingTrack

                                                if (name.isNotEmpty() && track != null) {
                                                    val updated =
                                                        playlists.toMutableMap()

                                                    updated[name] =
                                                        (updated[name].orEmpty() +
                                                            track.id).distinct()

                                                    playlists = updated
                                                    savePlaylists(updated)
                                                    selectedPlaylist = name
                                                }

                                                pendingTrack = null
                                                newPlaylistName = ""
                                            }
                                        ) {
                                            Text("Создать и добавить")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(
                                            onClick = {
                                                pendingTrack = null
                                                newPlaylistName = ""
                                            }
                                        ) {
                                            Text("Отмена")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadFavorites(): Set<Long> {
        val stored = getSharedPreferences(
            "pure_music_player",
            MODE_PRIVATE
        ).getString("favorites", "[]") ?: "[]"

        return try {
            val array = JSONArray(stored)

            buildSet {
                for (i in 0 until array.length()) {
                    add(array.optLong(i))
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun saveFavorites(favorites: Set<Long>) {
        val array = JSONArray()
        favorites.forEach { array.put(it) }

        getSharedPreferences("pure_music_player", MODE_PRIVATE)
            .edit()
            .putString("favorites", array.toString())
            .apply()
    }

    private fun loadPlaylists(): Map<String, List<Long>> {
        val stored = getSharedPreferences(
            "pure_music_player",
            MODE_PRIVATE
        ).getString("playlists", "{}") ?: "{}"

        return try {
            val json = JSONObject(stored)

            buildMap {
                val keys = json.keys()

                while (keys.hasNext()) {
                    val name = keys.next()
                    val array = json.optJSONArray(name) ?: JSONArray()

                    put(
                        name,
                        buildList {
                            for (i in 0 until array.length()) {
                                add(array.optLong(i))
                            }
                        }
                    )
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun savePlaylists(playlists: Map<String, List<Long>>) {
        val json = JSONObject()

        playlists.forEach { (name, ids) ->
            val array = JSONArray()
            ids.forEach { array.put(it) }
            json.put(name, array)
        }

        getSharedPreferences("pure_music_player", MODE_PRIVATE)
            .edit()
            .putString("playlists", json.toString())
            .apply()
    }

    private fun loadTracks(): List<Track> {
        if (ContextCompat.checkSelfPermission(this, permission) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val result = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID
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

            val albumColumn = cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.ALBUM_ID
            )

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)

                val artist = cursor.getString(artistColumn)
                    ?.takeUnless { it == "<unknown>" }
                    ?: "Неизвестный исполнитель"

                result += Track(
                    id = id,
                    title = cursor.getString(titleColumn) ?: "Без названия",
                    artist = artist,
                    uri = ContentUris.withAppendedId(collection, id),
                    albumId = cursor.getLong(albumColumn)
                )
            }
        }

        return result
    }

    private fun time(milliseconds: Long): String {
        val seconds = (milliseconds / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(seconds / 60L, seconds % 60L)
    }

    override fun onDestroy() {
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }

        controllerFuture = null
        super.onDestroy()
    }
}
