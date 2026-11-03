
package com.diriabas.puremusicplayer

import android.Manifest
import android.content.ContentUris
import android.content.ComponentName
import android.content.Intent
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import kotlin.math.abs
import kotlin.math.sin

private data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    val albumId: Long
)

private val Accent = Color(0xFFD6B878) // Champagne gold
private val Background = Color(0xFF0B0C0E)
private val Panel = Color(0xFF17191D)
private val PanelRaised = Color(0xFF202329)
private val MutedText = Color(0xFFA7A9AE)


private data class EqualizerPreset(
    val name: String,
    val levels: List<Int>
)

private val equalizerFrequencies = listOf(
    "31 Гц", "62 Гц", "125 Гц", "250 Гц", "500 Гц",
    "1 кГц", "2 кГц", "4 кГц", "8 кГц", "16 кГц"
)

private val equalizerPresets = listOf(
    EqualizerPreset("Flat", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
    EqualizerPreset("Rock", listOf(5, 4, 3, 1, -1, 1, 3, 4, 5, 5)),
    EqualizerPreset("Hard Rock", listOf(6, 5, 4, 2, -1, 2, 4, 5, 6, 6)),
    EqualizerPreset("Metal", listOf(5, 4, 3, 1, -2, 2, 4, 5, 4, 3)),
    EqualizerPreset("Heavy Metal", listOf(6, 5, 4, 1, -2, 2, 5, 6, 5, 4)),
    EqualizerPreset("Pop", listOf(-1, 1, 3, 4, 2, 0, -1, -1, 1, 2)),
    EqualizerPreset("Dance", listOf(5, 4, 2, 0, 1, 3, 4, 3, 2, 2)),
    EqualizerPreset("Electronic", listOf(5, 4, 2, 1, -1, 2, 3, 4, 5, 5)),
    EqualizerPreset("Techno", listOf(5, 4, 3, 1, 0, 2, 3, 4, 4, 4)),
    EqualizerPreset("House", listOf(4, 3, 2, 0, 1, 2, 3, 3, 4, 4)),
    EqualizerPreset("Hip-Hop", listOf(6, 5, 3, 1, -1, 1, 2, 2, 3, 4)),
    EqualizerPreset("Rap", listOf(5, 4, 2, 1, 0, 1, 2, 2, 3, 3)),
    EqualizerPreset("R&B", listOf(4, 3, 2, 1, 0, 1, 2, 3, 3, 3)),
    EqualizerPreset("Jazz", listOf(4, 3, 1, 2, -1, 1, 2, 3, 4, 4)),
    EqualizerPreset("Blues", listOf(3, 2, 1, 2, -1, 1, 2, 3, 3, 3)),
    EqualizerPreset("Soul", listOf(3, 2, 2, 1, 0, 1, 2, 3, 3, 3)),
    EqualizerPreset("Funk", listOf(4, 3, 1, 2, -1, 1, 3, 3, 3, 2)),
    EqualizerPreset("Classical", listOf(4, 3, 2, 1, -1, -1, 0, 2, 3, 4)),
    EqualizerPreset("Acoustic", listOf(3, 2, 1, 2, 0, 1, 2, 3, 3, 3)),
    EqualizerPreset("Vocal", listOf(-2, -1, 0, 2, 4, 4, 3, 2, 0, -1)),
    EqualizerPreset("Piano", listOf(2, 1, 0, 2, 3, 2, 1, 2, 3, 3)),
    EqualizerPreset("Bass Boost", listOf(7, 6, 5, 3, 1, 0, 0, 0, 0, 0)),
    EqualizerPreset("Deep Bass", listOf(8, 7, 5, 3, 1, 0, -1, -1, 0, 0)),
    EqualizerPreset("Treble Boost", listOf(0, 0, -1, 0, 1, 2, 3, 5, 6, 6)),
    EqualizerPreset("Loudness", listOf(4, 3, 1, 0, 0, 1, 2, 3, 3, 3))
)

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
                var equalizerDialogOpen by remember { mutableStateOf(false) }
                var equalizerEnabled by remember { mutableStateOf(loadEqualizerEnabled()) }
                var equalizerLevels by remember { mutableStateOf(loadEqualizerLevels()) }
                var bassStrength by remember { mutableIntStateOf(loadEffectStrength("bass", 0)) }
                var virtualizerStrength by remember { mutableIntStateOf(loadEffectStrength("virtualizer", 0)) }
                var presetMenuExpanded by remember { mutableStateOf(false) }
                var saveProfileDialogOpen by remember { mutableStateOf(false) }
                var profileName by remember { mutableStateOf("") }
                var customProfiles by remember { mutableStateOf(loadEqualizerProfiles()) }

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
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.2.sp
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
                                fontSize = 28.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.2.sp
                            )

                            Text(
                                text = "${tracks.size} аудиофайлов",
                                color = MutedText,
                                fontSize = 13.sp,
                                letterSpacing = 0.3.sp
                            )

                            Spacer(Modifier.height(18.dp))

                            if (currentTrack != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            color = Panel,
                                            shape = RoundedCornerShape(26.dp)
                                        )
                                        .padding(horizontal = 18.dp, vertical = 20.dp)
                                ) {
                                    Text(
                                        text = "NOW PLAYING",
                                        color = Accent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.8.sp
                                    )

                                    Spacer(Modifier.height(14.dp))
                                    AlbumCover(albumId = currentTrack.albumId)

                                    Spacer(Modifier.height(14.dp))
                                    PremiumSpectrum(isPlaying = playing)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = if (playing) "AUDIO PLAYING" else "PLAYBACK PAUSED",
                                        color = MutedText,
                                        fontSize = 9.sp,
                                        letterSpacing = 2.sp,
                                        fontWeight = FontWeight.Medium
                                    )

                                    Spacer(Modifier.height(18.dp))

                                    Text(
                                        text = currentTrack.title,
                                        color = Color.White,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.1.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Text(
                                        text = currentTrack.artist,
                                        color = MutedText,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(Modifier.height(18.dp))

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
                                            color = MutedText,
                                            fontSize = 11.sp
                                        )

                                        Text(
                                            text = time(duration),
                                            color = MutedText,
                                            fontSize = 11.sp
                                        )
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "◀",
                                            color = Accent,
                                            fontSize = 25.sp,
                                            modifier = Modifier.clickable {
                                                playAt(currentIndex - 1)
                                            }
                                        )

                                        Text(
                                            text = if (playing) "Ⅱ" else "▶",
                                            color = Accent,
                                            fontSize = 36.sp,
                                            modifier = Modifier
                                                .background(
                                                    color = PanelRaised,
                                                    shape = RoundedCornerShape(50)
                                                )
                                                .padding(horizontal = 25.dp, vertical = 10.dp)
                                                .clickable {
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
                                            fontSize = 25.sp,
                                            modifier = Modifier.clickable {
                                                playAt(currentIndex + 1)
                                            }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(18.dp))
                                HorizontalDivider(color = Color(0xFF34302A))
                                Spacer(Modifier.height(12.dp))
                            }

                            TextButton(
                                onClick = { equalizerDialogOpen = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = PanelRaised,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Text(
                                    "♫  ЭКВАЛАЙЗЕР И ЗВУК",
                                    color = Accent,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 1.1.sp
                                )
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
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp,
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
                                                        Color(0xFF302A20)
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


                            if (equalizerDialogOpen) {
                                AlertDialog(
                                    onDismissRequest = { equalizerDialogOpen = false },
                                    title = { Text("Эквалайзер", color = Color.White) },
                                    text = {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 520.dp)
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("Эквалайзер включён", color = Color.White)
                                                Switch(
                                                    checked = equalizerEnabled,
                                                    onCheckedChange = {
                                                        equalizerEnabled = it
                                                        sendEqualizerSettings(
                                                            it, equalizerLevels, bassStrength, virtualizerStrength
                                                        )
                                                    }
                                                )
                                            }

                                            Box {
                                                TextButton(onClick = { presetMenuExpanded = true }) {
                                                    Text("ПРЕСЕТЫ ▾", color = Accent)
                                                }
                                                DropdownMenu(
                                                    expanded = presetMenuExpanded,
                                                    onDismissRequest = { presetMenuExpanded = false }
                                                ) {
                                                    equalizerPresets.forEach { preset ->
                                                        DropdownMenuItem(
                                                            text = { Text(preset.name) },
                                                            onClick = {
                                                                equalizerLevels = preset.levels
                                                                presetMenuExpanded = false
                                                                sendEqualizerSettings(
                                                                    equalizerEnabled, equalizerLevels,
                                                                    bassStrength, virtualizerStrength
                                                                )
                                                            }
                                                        )
                                                    }
                                                    customProfiles.toSortedMap().forEach { (name, values) ->
                                                        DropdownMenuItem(
                                                            text = { Text("★ $name") },
                                                            onClick = {
                                                                equalizerLevels = values
                                                                presetMenuExpanded = false
                                                                sendEqualizerSettings(
                                                                    equalizerEnabled, equalizerLevels,
                                                                    bassStrength, virtualizerStrength
                                                                )
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            Text(
                                                "Частотные полосы · от −12 до +12 дБ",
                                                color = Color.LightGray,
                                                fontSize = 12.sp
                                            )

                                            equalizerFrequencies.forEachIndexed { index, label ->
                                                Column {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(label, color = Color.White, fontSize = 12.sp)
                                                        Text(
                                                            "${equalizerLevels[index]} дБ",
                                                            color = Accent,
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                    Slider(
                                                        value = equalizerLevels[index].toFloat(),
                                                        onValueChange = { value ->
                                                            equalizerLevels = equalizerLevels.toMutableList().also {
                                                                it[index] = value.toInt()
                                                            }
                                                        },
                                                        onValueChangeFinished = {
                                                            sendEqualizerSettings(
                                                                equalizerEnabled, equalizerLevels,
                                                                bassStrength, virtualizerStrength
                                                            )
                                                        },
                                                        valueRange = -12f..12f,
                                                        steps = 23,
                                                        enabled = equalizerEnabled,
                                                        colors = SliderDefaults.colors(
                                                            thumbColor = Accent,
                                                            activeTrackColor = Accent
                                                        )
                                                    )
                                                }
                                            }

                                            HorizontalDivider(color = Panel)
                                            Text("Усиление баса", color = Color.White)
                                            Slider(
                                                value = bassStrength.toFloat(),
                                                onValueChange = { bassStrength = it.toInt() },
                                                onValueChangeFinished = {
                                                    sendEqualizerSettings(
                                                        equalizerEnabled, equalizerLevels,
                                                        bassStrength, virtualizerStrength
                                                    )
                                                },
                                                valueRange = 0f..100f,
                                                enabled = equalizerEnabled,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = Accent,
                                                    activeTrackColor = Accent
                                                )
                                            )
                                            Text("$bassStrength %", color = Color.LightGray, fontSize = 12.sp)

                                            Text("Объёмное звучание", color = Color.White)
                                            Slider(
                                                value = virtualizerStrength.toFloat(),
                                                onValueChange = { virtualizerStrength = it.toInt() },
                                                onValueChangeFinished = {
                                                    sendEqualizerSettings(
                                                        equalizerEnabled, equalizerLevels,
                                                        bassStrength, virtualizerStrength
                                                    )
                                                },
                                                valueRange = 0f..100f,
                                                enabled = equalizerEnabled,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = Accent,
                                                    activeTrackColor = Accent
                                                )
                                            )
                                            Text("$virtualizerStrength %", color = Color.LightGray, fontSize = 12.sp)

                                            TextButton(onClick = {
                                                equalizerLevels = List(10) { 0 }
                                                bassStrength = 0
                                                virtualizerStrength = 0
                                                equalizerEnabled = true
                                                sendEqualizerSettings(
                                                    true, equalizerLevels, bassStrength, virtualizerStrength
                                                )
                                            }) {
                                                Text("СБРОСИТЬ НАСТРОЙКИ", color = Accent)
                                            }
                                            TextButton(onClick = {
                                                profileName = ""
                                                saveProfileDialogOpen = true
                                            }) {
                                                Text("СОХРАНИТЬ СВОЙ ПРОФИЛЬ", color = Accent)
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { equalizerDialogOpen = false }) {
                                            Text("Готово", color = Accent)
                                        }
                                    }
                                )
                            }

                            if (saveProfileDialogOpen) {
                                AlertDialog(
                                    onDismissRequest = { saveProfileDialogOpen = false },
                                    title = { Text("Сохранить профиль") },
                                    text = {
                                        OutlinedTextField(
                                            value = profileName,
                                            onValueChange = { profileName = it },
                                            singleLine = true,
                                            label = { Text("Название профиля") }
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            val name = profileName.trim()
                                            if (name.isNotEmpty()) {
                                                val updated = customProfiles.toMutableMap()
                                                updated[name] = equalizerLevels
                                                customProfiles = updated
                                                saveEqualizerProfiles(updated)
                                            }
                                            saveProfileDialogOpen = false
                                        }) {
                                            Text("Сохранить", color = Accent)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { saveProfileDialogOpen = false }) {
                                            Text("Отмена")
                                        }
                                    }
                                )
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


    private fun loadEqualizerEnabled(): Boolean =
        getSharedPreferences("pure_music_player", MODE_PRIVATE)
            .getBoolean("eq_enabled", true)

    private fun loadEqualizerLevels(): List<Int> {
        val stored = getSharedPreferences("pure_music_player", MODE_PRIVATE)
            .getString("eq_levels", null)
        val values = stored?.split(",")?.mapNotNull { it.toIntOrNull() }
        return if (values?.size == 10) values.map { it.coerceIn(-12, 12) }
        else List(10) { 0 }
    }

    private fun loadEffectStrength(key: String, fallback: Int): Int =
        getSharedPreferences("pure_music_player", MODE_PRIVATE)
            .getInt("eq_$key", fallback).coerceIn(0, 100)

    private fun sendEqualizerSettings(
        enabled: Boolean,
        levels: List<Int>,
        bass: Int,
        virtualizer: Int
    ) {
        getSharedPreferences("pure_music_player", MODE_PRIVATE).edit()
            .putBoolean("eq_enabled", enabled)
            .putString("eq_levels", levels.joinToString(","))
            .putInt("eq_bass", bass)
            .putInt("eq_virtualizer", virtualizer)
            .apply()

        val intent = Intent(this, PlaybackService::class.java).apply {
            action = PlaybackService.ACTION_UPDATE_EQUALIZER
            putExtra("eq_enabled", enabled)
            putExtra("eq_levels", levels.toIntArray())
            putExtra("bass_strength", bass)
            putExtra("virtualizer_strength", virtualizer)
        }
        startService(intent)
    }

    private fun loadEqualizerProfiles(): Map<String, List<Int>> {
        val stored = getSharedPreferences("pure_music_player", MODE_PRIVATE)
            .getString("eq_profiles", "{}") ?: "{}"
        return try {
            val json = JSONObject(stored)
            buildMap {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    val array = json.optJSONArray(name) ?: JSONArray()
                    val values = buildList {
                        for (i in 0 until array.length()) add(array.optInt(i))
                    }
                    if (values.size == 10) put(name, values.map { it.coerceIn(-12, 12) })
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveEqualizerProfiles(profiles: Map<String, List<Int>>) {
        val json = JSONObject()
        profiles.forEach { (name, values) ->
            val array = JSONArray()
            values.forEach { array.put(it) }
            json.put(name, array)
        }
        getSharedPreferences("pure_music_player", MODE_PRIVATE).edit()
            .putString("eq_profiles", json.toString())
            .apply()
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


@Composable
private fun PremiumSpectrum(isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "premium_spectrum")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spectrum_phase"
    )

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
    ) {
        val barCount = 36
        val gap = 4.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(1f)
        val maxHeight = size.height * 0.92f

        for (index in 0 until barCount) {
            val wave = abs(sin(phase + index * 0.47f)).toFloat()
            val detail = abs(sin(phase * 1.7f + index * 0.91f)).toFloat()
            val level = if (isPlaying) {
                0.16f + wave * 0.58f + detail * 0.26f
            } else {
                0.12f + abs(sin(index * 0.63f)).toFloat() * 0.12f
            }
            val barHeight = (maxHeight * level).coerceIn(4.dp.toPx(), maxHeight)
            val x = index * (barWidth + gap)
            val y = (size.height - barHeight) / 2f
            val alpha = if (isPlaying) 0.55f + wave * 0.45f else 0.42f

            drawRoundRect(
                color = Accent.copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

