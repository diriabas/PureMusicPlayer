
package com.diriabas.puremusicplayer

import android.Manifest
import android.content.ContentUris
import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
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
    val albumId: Long,
    val album: String,
    val dateAdded: Long,
    val durationMs: Long
)

// Тёплая светлая палитра с сохранением фирменной позолоты
private val Accent = Color(0xFFD6B878) // Champagne gold — фирменный акцент
private val Background = Color(0xFFFFF8EC) // Светлый сливочный фон
private val Panel = Color(0xFFFFFDF8) // Почти белые карточки
private val PanelRaised = Color(0xFFF3EBDD) // Тёплый оттенок для вложенных элементов
private val MutedText = Color(0xFF8B7962) // Приглушённый тёплый текст
private val TextPrimary = Color(0xFF493A2A) // Основной текст

private data class NavigationSnapshot(
    val tab: Int,
    val libraryMode: String,
    val selectedPlaylist: String?,
    val selectedAlbumId: Long?,
    val selectedArtist: String?
)

private data class PlayerTheme(
    val name: String,
    val background: Color,
    val panel: Color,
    val panelRaised: Color,
    val mutedText: Color,
    val textPrimary: Color
)

private val playerThemes = listOf(
    // Светлые темы
    PlayerTheme("Сливочный", Color(0xFFFFF8EC), Color(0xFFFFFDF8), Color(0xFFF3EBDD), Color(0xFF8B7962), Color(0xFF493A2A)),
    PlayerTheme("Нежно-серый", Color(0xFFF1F0EE), Color(0xFFFFFEFC), Color(0xFFE7E5E1), Color(0xFF77736D), Color(0xFF393733)),
    PlayerTheme("Светло-серый", Color(0xFFE5E7E9), Color(0xFFF7F8F9), Color(0xFFD5D9DD), Color(0xFF68717A), Color(0xFF303840)),
    PlayerTheme("Голубой", Color(0xFFEAF5FA), Color(0xFFF9FDFF), Color(0xFFD8EBF4), Color(0xFF668494), Color(0xFF294653)),
    PlayerTheme("Синий", Color(0xFFE8EFFB), Color(0xFFF8FAFF), Color(0xFFD5E1F4), Color(0xFF657895), Color(0xFF263B5A)),
    PlayerTheme("Зелёный", Color(0xFFEDF5EA), Color(0xFFFBFEF9), Color(0xFFDDEBD7), Color(0xFF6D8267), Color(0xFF344A30)),

    // Тёмные темы с неизменной шампань-позолотой
    PlayerTheme("Совсем тёмная", Color(0xFF090A0C), Color(0xFF15171A), Color(0xFF25282D), Color(0xFFB5B0A5), Color(0xFFF4F0E7)),
    PlayerTheme("Тёмно-синяя", Color(0xFF0B1424), Color(0xFF14243A), Color(0xFF203650), Color(0xFFA9B9CF), Color(0xFFEAF1FA)),
    PlayerTheme("Тёмно-бирюзовая", Color(0xFF09201F), Color(0xFF12312F), Color(0xFF1D4542), Color(0xFFA6C3BD), Color(0xFFE6F4EF)),
    PlayerTheme("Серая", Color(0xFF777B80), Color(0xFF898E94), Color(0xFF686D73), Color(0xFFF0F1F2), Color(0xFFFFFFFF)),
    PlayerTheme("Тёмно-серая", Color(0xFF25282C), Color(0xFF34383E), Color(0xFF454A51), Color(0xFFBFC4CA), Color(0xFFF1F3F5)),
    // Glass: полупрозрачные панели на глубоком сине-графитовом фоне
    PlayerTheme("Стеклянная", Color(0xFF101722), Color(0xB8223040), Color(0xD12D4054), Color(0xFFB7C7D8), Color(0xFFF1F6FC))
)


private data class EqualizerPreset(
    val name: String,
    val levels: List<Int>
)

private val equalizerFrequencies = listOf(
    "31 Гц", "62 Гц", "125 Гц", "250 Гц", "500 Гц",
    "1 кГц", "2 кГц", "4 кГц", "8 кГц", "16 кГц"
)

private val equalizerPresets = listOf(
    EqualizerPreset("Стандарт", listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
    EqualizerPreset("Бас", listOf(6, 5, 3, 1, 0, 0, 0, 0, 0, 0)),
    EqualizerPreset("Бас+", listOf(8, 7, 5, 3, 1, 0, -1, -1, 0, 0)),
    EqualizerPreset("Поп", listOf(-1, 1, 3, 4, 2, 0, -1, -1, 1, 2)),
    EqualizerPreset("Рок", listOf(5, 4, 3, 1, -1, 1, 3, 4, 5, 5)),
    EqualizerPreset("Классика", listOf(4, 3, 2, 1, -1, -1, 0, 2, 3, 4))
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
            // Reload the UI and music library after the permission flow.
            // Notifications are optional; the audio library must load either way.
            if (ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                recreate()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        installCrashLogger()

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
                    colorScheme = lightColorScheme(
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
                                color = TextPrimary
                            )
                        }
                    }
                }
            } else {
                var tracks by remember { mutableStateOf(loadTracks()) }
                var currentIndex by remember { mutableIntStateOf(-1) }
                var playbackQueue by remember { mutableStateOf(tracks) }
                var playing by remember { mutableStateOf(false) }
                var shuffleEnabled by remember { mutableStateOf(exoPlayer.shuffleModeEnabled) }
                var repeatMode by remember { mutableIntStateOf(exoPlayer.repeatMode) }
                var position by remember { mutableFloatStateOf(0f) }
                var duration by remember { mutableLongStateOf(0L) }
                var playlists by remember { mutableStateOf(loadPlaylists()) }
                var selectedPlaylist by remember { mutableStateOf<String?>(null) }
                val playerPreferences = remember {
                    getSharedPreferences("pure_music_player", MODE_PRIVATE)
                }
                var selectedThemeName by remember {
                    mutableStateOf(playerPreferences.getString("player_theme", "Сливочный") ?: "Сливочный")
                }
                val currentTheme = playerThemes.firstOrNull { it.name == selectedThemeName } ?: playerThemes.first()
                val Accent = Color(0xFFD6B878)
                val Background = currentTheme.background
                val Panel = currentTheme.panel
                val PanelRaised = currentTheme.panelRaised
                val MutedText = currentTheme.mutedText
                val TextPrimary = currentTheme.textPrimary

                var libraryMode by remember {
                    mutableStateOf(playerPreferences.getString("library_mode", "Композиции") ?: "Композиции")
                }
                var selectedTab by remember {
                    mutableIntStateOf(playerPreferences.getInt("selected_tab", 0).coerceIn(0, 4))
                }

                LaunchedEffect(libraryMode) {
                    playerPreferences.edit().putString("library_mode", libraryMode).apply()
                }
                LaunchedEffect(selectedTab) {
                    playerPreferences.edit().putInt("selected_tab", selectedTab).apply()
                }

                var createPlaylistDialogOpen by remember { mutableStateOf(false) }
                var playlistNameInput by remember { mutableStateOf("") }
                var selectedAlbumId by remember { mutableStateOf<Long?>(null) }
                var selectedArtist by remember { mutableStateOf<String?>(null) }

                val navigationBackStack = remember { mutableStateListOf<NavigationSnapshot>() }

                fun currentNavigationSnapshot() = NavigationSnapshot(
                    tab = selectedTab,
                    libraryMode = libraryMode,
                    selectedPlaylist = selectedPlaylist,
                    selectedAlbumId = selectedAlbumId,
                    selectedArtist = selectedArtist
                )

                fun navigateTo(
                    tab: Int = selectedTab,
                    mode: String = libraryMode,
                    playlist: String? = selectedPlaylist,
                    albumId: Long? = selectedAlbumId,
                    artist: String? = selectedArtist
                ) {
                    val current = currentNavigationSnapshot()
                    val destination = NavigationSnapshot(tab, mode, playlist, albumId, artist)
                    if (current != destination) {
                        navigationBackStack.add(current)
                        selectedTab = destination.tab
                        libraryMode = destination.libraryMode
                        selectedPlaylist = destination.selectedPlaylist
                        selectedAlbumId = destination.selectedAlbumId
                        selectedArtist = destination.selectedArtist
                    }
                }

                fun goBack() {
                    if (navigationBackStack.isNotEmpty()) {
                        val previous = navigationBackStack.removeAt(navigationBackStack.lastIndex)
                        selectedTab = previous.tab
                        libraryMode = previous.libraryMode
                        selectedPlaylist = previous.selectedPlaylist
                        selectedAlbumId = previous.selectedAlbumId
                        selectedArtist = previous.selectedArtist
                    } else if (selectedTab != 0 || selectedPlaylist != null ||
                        selectedAlbumId != null || selectedArtist != null) {
                        selectedTab = 0
                        selectedPlaylist = null
                        selectedAlbumId = null
                        selectedArtist = null
                        libraryMode = "Композиции"
                    }
                }

                BackHandler(enabled = navigationBackStack.isNotEmpty() || selectedTab != 0 ||
                    selectedPlaylist != null || selectedAlbumId != null || selectedArtist != null) {
                    goBack()
                }
                var sortMode by remember { mutableStateOf("По названию") }
                var sortMenuExpanded by remember { mutableStateOf(false) }
                var playlistMenuExpanded by remember { mutableStateOf(false) }
                var playlistMenuName by remember { mutableStateOf<String?>(null) }
                var playlistToRename by remember { mutableStateOf<String?>(null) }
                var renamePlaylistInput by remember { mutableStateOf("") }
                var playlistToDelete by remember { mutableStateOf<String?>(null) }
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
                var sleepTimerDialogOpen by remember { mutableStateOf(false) }
                var sleepTimerMinutes by remember { mutableIntStateOf(0) }
                var sleepTimerRemaining by remember { mutableLongStateOf(0L) }

                LaunchedEffect(sleepTimerMinutes) {
                    if (sleepTimerMinutes > 0) {
                        sleepTimerRemaining = sleepTimerMinutes * 60_000L
                        while (sleepTimerRemaining > 0L) {
                            delay(1_000L)
                            sleepTimerRemaining = (sleepTimerRemaining - 1_000L).coerceAtLeast(0L)
                        }
                        sleepTimerMinutes = 0
                        Toast.makeText(this@MainActivity, "Таймер сна: воспроизведение остановлено", Toast.LENGTH_SHORT).show()
                    } else {
                        sleepTimerRemaining = 0L
                    }
                }

                val visibleTracks = remember(
                    tracks, playlists, selectedPlaylist, sortMode,
                    searchQuery, favoritesOnly, favoriteIds,
                    selectedAlbumId, selectedArtist
                ) {
                    val base = if (selectedPlaylist == null) tracks else {
                        // Playlist order is stored explicitly in its list of track IDs.
                        playlists[selectedPlaylist].orEmpty().mapNotNull { id ->
                            tracks.firstOrNull { it.id == id }
                        }
                    }

                    val filtered = base.filter { track ->
                        val matchesSearch = searchQuery.isBlank() ||
                            track.title.contains(searchQuery, ignoreCase = true) ||
                            track.artist.contains(searchQuery, ignoreCase = true) ||
                            track.album.contains(searchQuery, ignoreCase = true)

                        val matchesFavorites = !favoritesOnly ||
                            track.id in favoriteIds
                        val matchesAlbum = selectedAlbumId == null ||
                            track.albumId == selectedAlbumId
                        val matchesArtist = selectedArtist == null ||
                            track.artist == selectedArtist

                        matchesSearch && matchesFavorites && matchesAlbum && matchesArtist
                    }

                    if (selectedPlaylist != null) {
                        // Manual playlist order takes precedence over library sorting.
                        filtered
                    } else {
                        when (sortMode) {
                            "По исполнителю" -> filtered.sortedWith(
                                compareBy<Track> { it.artist.lowercase() }
                                    .thenBy { it.title.lowercase() }
                            )
                            "Сначала новые" -> filtered.sortedWith(
                                compareByDescending<Track> { it.dateAdded }
                                    .thenBy { it.title.lowercase() }
                            )
                            "Сначала старые" -> filtered.sortedWith(
                                compareBy<Track> { it.dateAdded }
                                    .thenBy { it.title.lowercase() }
                            )
                            "По длительности ↑" -> filtered.sortedWith(
                                compareBy<Track> { it.durationMs }
                                    .thenBy { it.title.lowercase() }
                            )
                            "По длительности ↓" -> filtered.sortedWith(
                                compareByDescending<Track> { it.durationMs }
                                    .thenBy { it.title.lowercase() }
                            )
                            else -> filtered.sortedBy { it.title.lowercase() }
                        }
                    }
                }

                val dragThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
                val movePlaylistTrack: (Long, Int) -> Unit = { trackId, direction ->
                    val playlistName = selectedPlaylist
                    if (playlistName != null) {
                        val orderedIds = playlists[playlistName].orEmpty().toMutableList()
                        val fromIndex = orderedIds.indexOf(trackId)
                        val toIndex = fromIndex + direction
                        if (fromIndex >= 0 && toIndex in orderedIds.indices) {
                            val movedId = orderedIds.removeAt(fromIndex)
                            orderedIds.add(toIndex, movedId)
                            val updated = playlists.toMutableMap()
                            updated[playlistName] = orderedIds
                            playlists = updated
                            savePlaylists(updated)
                        }
                    }
                }

                val currentTrack = tracks.getOrNull(currentIndex)

                // Restore the previous queue and playback position after process restart.
                LaunchedEffect(exoPlayer, tracks) {
                    if (tracks.isNotEmpty() && exoPlayer.mediaItemCount == 0) {
                        val savedIds = playerPreferences.getString("last_queue_ids", null)
                            ?.split(",")?.mapNotNull { it.toLongOrNull() }.orEmpty()
                        val restoredQueue = savedIds.mapNotNull { id -> tracks.find { it.id == id } }
                        val queueToRestore = restoredQueue.ifEmpty { tracks }
                        val savedTrackId = playerPreferences.getLong("last_track_id", -1L)
                        val restoredIndex = queueToRestore.indexOfFirst { it.id == savedTrackId }
                            .takeIf { it >= 0 } ?: playerPreferences.getInt("last_queue_index", 0)
                                .coerceIn(0, queueToRestore.lastIndex)
                        val savedPosition = playerPreferences.getLong("last_position_ms", 0L)
                            .coerceAtLeast(0L)
                        val shouldResume = playerPreferences.getBoolean("was_playing", false)
                        val mediaItems = queueToRestore.map { track ->
                            val metadata = MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artist)
                                .setArtworkUri(Uri.parse(
                                    "content://media/external/audio/albumart/${track.albumId}"
                                ))
                                .build()
                            MediaItem.Builder()
                                .setMediaId(track.id.toString())
                                .setUri(track.uri)
                                .setMediaMetadata(metadata)
                                .build()
                        }
                        playbackQueue = queueToRestore
                        exoPlayer.setMediaItems(mediaItems, restoredIndex, savedPosition)
                        exoPlayer.prepare()
                        currentIndex = tracks.indexOfFirst { it.id == queueToRestore[restoredIndex].id }
                        if (shouldResume) exoPlayer.play()
                        playing = shouldResume
                    }
                }

                fun playAt(track: Track, queue: List<Track> = tracks) {
                    val queueIndex = queue.indexOfFirst { it.id == track.id }
                    if (queueIndex < 0) return

                    playbackQueue = queue
                    currentIndex = tracks.indexOfFirst { it.id == track.id }
                    playerPreferences.edit()
                        .putString("last_queue_ids", queue.joinToString(",") { it.id.toString() })
                        .putInt("last_queue_index", queueIndex)
                        .putLong("last_track_id", track.id)
                        .putLong("last_position_ms", 0L)
                        .putBoolean("was_playing", true)
                        .apply()

                    val mediaItems = queue.map { queueTrack ->
                        val metadata = MediaMetadata.Builder()
                            .setTitle(queueTrack.title)
                            .setArtist(queueTrack.artist)
                            .setArtworkUri(
                                Uri.parse(
                                    "content://media/external/audio/albumart/${queueTrack.albumId}"
                                )
                            )
                            .build()

                        MediaItem.Builder()
                            .setMediaId(queueTrack.id.toString())
                            .setUri(queueTrack.uri)
                            .setMediaMetadata(metadata)
                            .build()
                    }

                    exoPlayer.setMediaItems(mediaItems, queueIndex, 0L)
                    exoPlayer.prepare()
                    exoPlayer.play()
                    playing = true
                }

                LaunchedEffect(exoPlayer) {
                    while (true) {
                        val serviceIndex = exoPlayer.currentMediaItemIndex

                        val activeTrack = playbackQueue.getOrNull(serviceIndex)
                        if (activeTrack != null) {
                            currentIndex = tracks.indexOfFirst { it.id == activeTrack.id }
                        }

                        playing = exoPlayer.isPlaying
                        if (activeTrack != null) {
                            playerPreferences.edit()
                                .putString("last_queue_ids", playbackQueue.joinToString(",") { it.id.toString() })
                                .putInt("last_queue_index", serviceIndex.coerceAtLeast(0))
                                .putLong("last_track_id", activeTrack.id)
                                .putLong("last_position_ms", exoPlayer.currentPosition.coerceAtLeast(0L))
                                .putBoolean("was_playing", exoPlayer.isPlaying)
                                .apply()
                        }
                        shuffleEnabled = exoPlayer.shuffleModeEnabled
                        repeatMode = exoPlayer.repeatMode
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
                    colorScheme = lightColorScheme(
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
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (selectedTab != 0) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 34.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "PURE MUSIC",
                                        color = Accent,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 1.5.sp
                                    )

                                    AvMonogram(size = 34.dp)
                                }
                            } else {
                                Spacer(Modifier.height(8.dp))
                            }


                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                when (selectedTab) {
                                    0 -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "Твоя музыка. Твой мир.",
                                                color = MutedText,
                                                fontSize = 13.sp,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 26.dp, bottom = 16.dp)
                                            )
                            if (currentTrack != null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AlbumCover(albumId = currentTrack.albumId, themeSurface = Panel)

                                    Spacer(Modifier.height(8.dp))
                                    PremiumSpectrum(isPlaying = playing)
                                    Spacer(Modifier.height(18.dp))

                                    Text(
                                        text = currentTrack.title,
                                        color = TextPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.05.sp,
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
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(18.dp),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Accent,
                                            activeTrackColor = Accent,
                                            inactiveTrackColor = MutedText.copy(alpha = 0.38f)
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
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .offset(y = (-6).dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "⤨",
                                            color = if (shuffleEnabled) Accent else MutedText,
                                            fontSize = 30.sp,
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                exoPlayer.shuffleModeEnabled = !exoPlayer.shuffleModeEnabled
                                                shuffleEnabled = exoPlayer.shuffleModeEnabled
                                            }
                                        )

                                        Text(
                                            text = "◀",
                                            color = Color(0xFFE5C982),
                                            fontSize = 30.sp,
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                exoPlayer.seekToPreviousMediaItem()
                                            }
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    if (exoPlayer.isPlaying) {
                                                        exoPlayer.pause()
                                                    } else {
                                                        exoPlayer.play()
                                                    }
                                                    playing = exoPlayer.isPlaying
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                // Play/pause glyph stays centered; no decorative circle behind it.
                                                val cx = size.width / 2f
                                                val cy = size.height / 2f
                                                if (playing) {
                                                    val barWidth = 5.dp.toPx()
                                                    val barHeight = 22.dp.toPx()
                                                    val gap = 5.dp.toPx()
                                                    drawRoundRect(
                                                        color = Accent,
                                                        topLeft = Offset(cx - gap / 2f - barWidth, cy - barHeight / 2f),
                                                        size = Size(barWidth, barHeight),
                                                        cornerRadius = CornerRadius(1.5.dp.toPx())
                                                    )
                                                    drawRoundRect(
                                                        color = Accent,
                                                        topLeft = Offset(cx + gap / 2f, cy - barHeight / 2f),
                                                        size = Size(barWidth, barHeight),
                                                        cornerRadius = CornerRadius(1.5.dp.toPx())
                                                    )
                                                } else {
                                                    val halfHeight = 13.dp.toPx()
                                                    val left = 10.dp.toPx()
                                                    val right = 16.dp.toPx()
                                                    val triangle = androidx.compose.ui.graphics.Path().apply {
                                                        moveTo(cx - left, cy - halfHeight)
                                                        lineTo(cx - left, cy + halfHeight)
                                                        lineTo(cx + right, cy)
                                                        close()
                                                    }
                                                    drawPath(path = triangle, color = Accent)
                                                }
                                            }
                                        }

                                        Text(
                                            text = "▶",
                                            color = Color(0xFFE5C982),
                                            fontSize = 30.sp,
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                exoPlayer.seekToNextMediaItem()
                                            }
                                        )

                                        Text(
                                            text = when (repeatMode) {
                                                Player.REPEAT_MODE_ONE -> "1↻"
                                                Player.REPEAT_MODE_ALL -> "↻"
                                                else -> "↻"
                                            },
                                            color = if (repeatMode == Player.REPEAT_MODE_OFF) MutedText else Accent,
                                            fontSize = 30.sp,
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                val nextMode = when (exoPlayer.repeatMode) {
                                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                                    else -> Player.REPEAT_MODE_OFF
                                                }
                                                exoPlayer.repeatMode = nextMode
                                                repeatMode = nextMode
                                            }
                                        )
                                    }
                                }

                                Spacer(Modifier.height(18.dp))
                                HorizontalDivider(color = Color(0xFFE5D7BD))
                                Spacer(Modifier.height(12.dp))
                            }


                                            if (currentTrack == null) {
                                                Text(
                                                    text = "Выбери композицию во вкладке «Музыка»",
                                                    color = MutedText,
                                                    modifier = Modifier.padding(vertical = 36.dp)
                                                )
                                            }
                                        }
                                    }

                                    1 -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(horizontal = 18.dp, vertical = 12.dp)
                                        ) {
                                            Text(
                                                text = "Музыка",
                                                color = TextPrimary,
                                                fontSize = 27.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "${tracks.size} аудиофайлов",
                                                color = MutedText,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(top = 3.dp, bottom = 16.dp)
                                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                listOf("Композиции", "Альбомы", "Исполнители").forEach { mode ->
                                    val isSelected = libraryMode == mode
                                    Text(
                                        text = mode,
                                        color = if (isSelected) TextPrimary else Accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                if (isSelected) Accent else Panel,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                navigateTo(tab = 1, mode = mode, playlist = null, albumId = null, artist = null)
                                            }
                                            .padding(vertical = 11.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(10.dp))



                            if (selectedPlaylist != null || selectedAlbumId != null || selectedArtist != null) {
                                TextButton(
                                    onClick = { goBack() }
                                ) {
                                    Text(
                                        if (selectedPlaylist != null) "← Все композиции" else "← Назад к библиотеке",
                                        color = Accent
                                    )
                                }
                            }

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

                            if (libraryMode == "Композиции" && selectedPlaylist == null) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Сортировка:",
                                        color = MutedText,
                                        fontSize = 13.sp
                                    )
                                    Box {
                                        TextButton(onClick = { sortMenuExpanded = true }) {
                                            Text(
                                                sortMode,
                                                color = Accent,
                                                fontSize = 13.sp
                                            )
                                            Text(" ▾", color = Accent)
                                        }
                                        DropdownMenu(
                                            expanded = sortMenuExpanded,
                                            onDismissRequest = { sortMenuExpanded = false },
                                            containerColor = Panel
                                        ) {
                                            listOf(
                                                "По названию",
                                                "По исполнителю",
                                                "Сначала новые",
                                                "Сначала старые",
                                                "По длительности ↑",
                                                "По длительности ↓"
                                            ).forEach { option ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            option,
                                                            color = if (sortMode == option) Accent else TextPrimary
                                                        )
                                                    },
                                                    onClick = {
                                                        sortMode = option
                                                        sortMenuExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (libraryMode == "Альбомы" && selectedAlbumId == null) {
                                val albums = visibleTracks.groupBy { it.albumId }
                                    .values
                                    .sortedBy { it.first().album.lowercase() }
                                albums.forEach { albumTracks ->
                                    val sample = albumTracks.first()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Panel, RoundedCornerShape(13.dp))
                                            .clickable {
                                                navigateTo(tab = 1, mode = "Композиции", playlist = null, albumId = sample.albumId, artist = null)
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AlbumThumbnail(albumId = sample.albumId, themeSurface = Panel)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(sample.album, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                            Text(sample.artist, color = MutedText, fontSize = 12.sp)
                                        }
                                        Text("${albumTracks.size} тр.", color = Accent, fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.height(7.dp))
                                }
                            } else if (libraryMode == "Исполнители" && selectedArtist == null) {
                                visibleTracks.groupBy { it.artist }
                                    .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                                    .forEach { (artistName, artistTracks) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Panel, RoundedCornerShape(13.dp))
                                                .clickable {
                                                    navigateTo(tab = 1, mode = "Композиции", playlist = null, albumId = null, artist = artistName)
                                                }
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .background(PanelRaised, RoundedCornerShape(12.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(artistName.take(1).uppercase(), color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Text(artistName, color = TextPrimary, modifier = Modifier.weight(1f))
                                            Text("${artistTracks.size} тр.", color = Accent, fontSize = 12.sp)
                                        }
                                        Spacer(Modifier.height(7.dp))
                                    }
                            } else if (selectedPlaylist != null) {
                                Text(
                                    "Чтобы изменить порядок, удерживай значок ⠿ и перетаскивай песню",
                                    color = MutedText,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                if (visibleTracks.isEmpty()) {
                                    Text("В этом плейлисте пока нет песен.", color = MutedText)
                                } else {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(7.dp)
                                    ) {
                                        visibleTracks.forEach { track ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        color = if (track.id == currentTrack?.id) Color(0xFFF1E4CC) else Panel,
                                                        shape = RoundedCornerShape(13.dp)
                                                    )
                                                    .clickable { playAt(track, visibleTracks) }
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AlbumThumbnail(albumId = track.albumId, themeSurface = Panel)
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                    "⠿",
                                                    color = Accent,
                                                    fontSize = 24.sp,
                                                    modifier = Modifier
                                                        .pointerInput(track.id, selectedPlaylist, dragThresholdPx) {
                                                            var accumulatedDrag = 0f
                                                            detectDragGestures(
                                                                onDrag = { change, dragAmount ->
                                                                    change.consume()
                                                                    accumulatedDrag += dragAmount.y
                                                                    if (accumulatedDrag >= dragThresholdPx) {
                                                                        movePlaylistTrack(track.id, 1)
                                                                        accumulatedDrag -= dragThresholdPx
                                                                    } else if (accumulatedDrag <= -dragThresholdPx) {
                                                                        movePlaylistTrack(track.id, -1)
                                                                        accumulatedDrag += dragThresholdPx
                                                                    }
                                                                }
                                                            )
                                                        }
                                                        .padding(horizontal = 7.dp, vertical = 4.dp)
                                                )
                                                Column(Modifier.weight(1f)) {
                                                    Text(
                                                        track.title,
                                                        color = TextPrimary,
                                                        fontSize = 15.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        track.artist,
                                                        color = MutedText,
                                                        fontSize = 12.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier.size(40.dp).clickable {
                                                            val name = selectedPlaylist
                                                            if (name != null) {
                                                                val updated = playlists.toMutableMap()
                                                                updated[name] = updated[name].orEmpty().filterNot { it == track.id }
                                                                playlists = updated
                                                                savePlaylists(updated)
                                                            }
                                                        },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("−", color = Accent, fontSize = 23.sp)
                                                    }
                                                    Box(
                                                        modifier = Modifier.size(40.dp).clickable {
                                                            val updated = favoriteIds.toMutableSet()
                                                            if (track.id in updated) updated.remove(track.id) else updated.add(track.id)
                                                            favoriteIds = updated
                                                            saveFavorites(updated)
                                                        },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(if (track.id in favoriteIds) "★" else "☆", color = Accent, fontSize = 21.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else if (visibleTracks.isEmpty()) {
                                Text(
                                    text = if (selectedPlaylist == null) {
                                        "Музыка не найдена. Проверьте разрешение на доступ к аудиофайлам."
                                    } else {
                                        "В этом плейлисте пока нет песен."
                                    },
                                    color = MutedText
                                )
                            } else {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    visibleTracks.forEach { track ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    color = if (track.id == currentTrack?.id) {
                                                        Color(0xFFF1E4CC)
                                                    } else {
                                                        Panel
                                                    },
                                                    shape = RoundedCornerShape(13.dp)
                                                )
                                                .clickable {
                                                    playAt(track, visibleTracks)
                                                }
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AlbumThumbnail(albumId = track.albumId, themeSurface = Panel)

                                            Spacer(Modifier.width(12.dp))

                                            Column(
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = track.title,
                                                    color = TextPrimary,
                                                    fontSize = 15.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )

                                                Text(
                                                    text = track.artist,
                                                    color = MutedText,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(40.dp).clickable {
                                                        val playlistName = selectedPlaylist
                                                        if (playlistName != null) {
                                                            val updated = playlists.toMutableMap()
                                                            updated[playlistName] = updated[playlistName]
                                                                .orEmpty()
                                                                .filterNot { it == track.id }
                                                            playlists = updated
                                                            savePlaylists(updated)
                                                        } else {
                                                            pendingTrack = track
                                                        }
                                                    },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(if (selectedPlaylist != null) "−" else "＋", color = Accent, fontSize = 23.sp)
                                                }
                                                Box(
                                                    modifier = Modifier.size(40.dp).clickable {
                                                        val updated = favoriteIds.toMutableSet()
                                                        if (!updated.add(track.id)) updated.remove(track.id)
                                                        favoriteIds = updated
                                                        saveFavorites(updated)
                                                    },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(if (track.id in favoriteIds) "★" else "☆", color = Accent, fontSize = 21.sp)
                                                }
                                                Box(
                                                    modifier = Modifier.size(40.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        if (track.id == currentTrack?.id && playing) "Ⅱ" else "▶",
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

                                    2 -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(horizontal = 18.dp, vertical = 12.dp)
                                        ) {
                                            Text(
                                                text = "Плейлисты",
                                                color = TextPrimary,
                                                fontSize = 27.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Твои музыкальные подборки",
                                                color = MutedText,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(top = 3.dp, bottom = 18.dp)
                                            )
                                            TextButton(
                                                onClick = {
                                                    playlistNameInput = ""
                                                    createPlaylistDialogOpen = true
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(PanelRaised, RoundedCornerShape(14.dp))
                                            ) {
                                                Text("+  СОЗДАТЬ ПЛЕЙЛИСТ", color = Accent, fontWeight = FontWeight.SemiBold)
                                            }
                                            Spacer(Modifier.height(12.dp))
                                            if (playlists.isEmpty()) {
                                                Text(
                                                    text = "Пока нет плейлистов. Создай первую подборку или добавляй песни в плейлист из раздела «Музыка».",
                                                    color = MutedText,
                                                    modifier = Modifier.padding(vertical = 16.dp)
                                                )
                                            } else {
                                                playlists.toSortedMap().forEach { (name, ids) ->
                                                    val playlistTracks = tracks.filter { it.id in ids }
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(bottom = 8.dp)
                                                            .background(Panel, RoundedCornerShape(14.dp))
                                                            .clickable {
                                                                navigateTo(tab = 1, mode = "Композиции", playlist = name, albumId = null, artist = null)
                                                            }
                                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("♫", color = Accent, fontSize = 24.sp)
                                                        Spacer(Modifier.width(12.dp))
                                                        Column(Modifier.weight(1f)) {
                                                            Text(name, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                                            Text("${playlistTracks.size} композиций", color = MutedText, fontSize = 12.sp)
                                                        }
                                                        if (playlistTracks.isNotEmpty()) {
                                                            Text(
                                                                "▶",
                                                                color = Accent,
                                                                fontSize = 18.sp,
                                                                modifier = Modifier
                                                                    .clickable { playAt(playlistTracks.first(), playlistTracks) }
                                                                    .padding(horizontal = 9.dp, vertical = 8.dp)
                                                            )
                                                        }
                                                        Box {
                                                            Text(
                                                                "⋮",
                                                                color = Accent,
                                                                fontSize = 23.sp,
                                                                modifier = Modifier
                                                                    .clickable {
                                                                        playlistMenuName = name
                                                                        playlistMenuExpanded = true
                                                                    }
                                                                    .padding(horizontal = 7.dp, vertical = 5.dp)
                                                            )
                                                            DropdownMenu(
                                                                expanded = playlistMenuExpanded && playlistMenuName == name,
                                                                onDismissRequest = {
                                                                    playlistMenuExpanded = false
                                                                    playlistMenuName = null
                                                                }
                                                            ) {
                                                                DropdownMenuItem(
                                                                    text = { Text("Переименовать") },
                                                                    onClick = {
                                                                        renamePlaylistInput = name
                                                                        playlistToRename = name
                                                                        playlistMenuExpanded = false
                                                                        playlistMenuName = null
                                                                    }
                                                                )
                                                                DropdownMenuItem(
                                                                    text = { Text("Удалить плейлист") },
                                                                    onClick = {
                                                                        playlistToDelete = name
                                                                        playlistMenuExpanded = false
                                                                        playlistMenuName = null
                                                                    }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    3 -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(horizontal = 18.dp, vertical = 12.dp)
                                        ) {
                                            Text(
                                                text = "Звук",
                                                color = TextPrimary,
                                                fontSize = 27.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Настрой звучание под себя",
                                                color = MutedText,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(top = 3.dp, bottom = 18.dp)
                                            )
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Panel, RoundedCornerShape(18.dp))
                                                    .padding(16.dp)
                                            ) {
                                                Text("Эквалайзер · 10 полос", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                                Text("6 готовых пресетов и ручная настройка", color = MutedText, fontSize = 12.sp)
                                                Spacer(Modifier.height(14.dp))
                                                Text("Усиление баса", color = TextPrimary)
                                                Text("${bassStrength} %", color = Accent, fontSize = 12.sp)
                                                Spacer(Modifier.height(8.dp))
                                                Text("Объёмное звучание", color = TextPrimary)
                                                Text("${virtualizerStrength} %", color = Accent, fontSize = 12.sp)
                                                Spacer(Modifier.height(16.dp))
                                                Button(
                                                    onClick = { equalizerDialogOpen = true },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Background)
                                                ) {
                                                    Text("ОТКРЫТЬ ЭКВАЛАЙЗЕР")
                                                }
                                            }
                                        }
                                    }

                                    else -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(rememberScrollState())
                                                .padding(horizontal = 18.dp, vertical = 12.dp)
                                        ) {
                                            Text(
                                                text = "Настройки",
                                                color = TextPrimary,
                                                fontSize = 27.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Параметры Pure Music Player",
                                                color = MutedText,
                                                fontSize = 13.sp,
                                                modifier = Modifier.padding(top = 3.dp, bottom = 18.dp)
                                            )

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Panel, RoundedCornerShape(14.dp))
                                                    .padding(16.dp)
                                            ) {
                                                Text("Цвет оформления", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                                Text("Выбери фон. Позолота и вензель AV остаются неизменными.", color = MutedText, fontSize = 12.sp)
                                                Spacer(Modifier.height(12.dp))
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    playerThemes.forEach { theme ->
                                                        val selected = selectedThemeName == theme.name
                                                        Column(
                                                            modifier = Modifier
                                                                .size(112.dp)
                                                                .background(theme.background, RoundedCornerShape(14.dp))
                                                                .border(
                                                                    width = if (selected) 2.dp else 1.dp,
                                                                    color = if (selected) Accent else theme.panelRaised,
                                                                    shape = RoundedCornerShape(14.dp)
                                                                )
                                                                .clickable {
                                                                    selectedThemeName = theme.name
                                                                    playerPreferences.edit().putString("player_theme", theme.name).apply()
                                                                }
                                                                .padding(8.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.Center
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(30.dp)
                                                                    .background(theme.panelRaised, RoundedCornerShape(9.dp))
                                                                    .border(1.dp, Accent.copy(alpha = 0.85f), RoundedCornerShape(9.dp))
                                                            )
                                                            Spacer(Modifier.height(8.dp))
                                                            Text(
                                                                theme.name,
                                                                color = theme.textPrimary,
                                                                fontSize = 11.sp,
                                                                lineHeight = 13.sp,
                                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(10.dp))
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Panel, RoundedCornerShape(14.dp))
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("♫", color = Accent, fontSize = 24.sp)
                                                Spacer(Modifier.width(14.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text("Музыкальная библиотека", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                                    Text("${tracks.size} аудиофайлов найдено", color = MutedText, fontSize = 12.sp)
                                                }
                                            }
                                            Spacer(Modifier.height(10.dp))
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Panel, RoundedCornerShape(14.dp))
                                                    .padding(16.dp)
                                            ) {
                                                Text("Таймер сна", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    if (sleepTimerMinutes > 0) {
                                                        val mins = (sleepTimerRemaining / 60_000L).toInt()
                                                        val secs = ((sleepTimerRemaining % 60_000L) / 1_000L).toInt()
                                                        "Остановка через %02d:%02d".format(mins, secs)
                                                    } else "Автоматическая остановка музыки",
                                                    color = MutedText,
                                                    fontSize = 12.sp
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    TextButton(onClick = { sleepTimerDialogOpen = true }) {
                                                        Text("УСТАНОВИТЬ", color = Accent)
                                                    }
                                                    if (sleepTimerMinutes > 0) {
                                                        TextButton(onClick = {
                                                            sleepTimerMinutes = 0
                                                            startService(Intent(this@MainActivity, PlaybackService::class.java).setAction(PlaybackService.ACTION_CANCEL_SLEEP_TIMER))
                                                        }) {
                                                            Text("ОТМЕНИТЬ", color = MutedText)
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(10.dp))
                                            TextButton(
                                                onClick = { tracks = loadTracks() },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(PanelRaised, RoundedCornerShape(14.dp))
                                            ) {
                                                Text("↻  ОБНОВИТЬ МУЗЫКАЛЬНУЮ БИБЛИОТЕКУ", color = Accent)
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            TextButton(
                                                onClick = {
                                                    val crashFile = java.io.File(filesDir, "last_crash.txt")
                                                    if (crashFile.exists()) {
                                                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                                                        clipboard.setPrimaryClip(
                                                            ClipData.newPlainText("Pure Music Player crash log", crashFile.readText())
                                                        )
                                                        Toast.makeText(this@MainActivity, "Журнал сбоя скопирован. Вставь его в чат.", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(this@MainActivity, "Сохранённых сбоев пока нет.", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(PanelRaised, RoundedCornerShape(14.dp))
                                            ) {
                                                Text("СКОПИРОВАТЬ ЖУРНАЛ СБОЯ", color = Accent)
                                            }
                                            Spacer(Modifier.height(10.dp))
                                            Text(
                                                text = "PURE MUSIC  ·  PREMIUM HI-FI",
                                                color = Accent,
                                                fontSize = 12.sp,
                                                letterSpacing = 1.1.sp,
                                                modifier = Modifier.padding(top = 18.dp)
                                            )
                                            Text("Чёрно-золотая тема · Локальное воспроизведение", color = MutedText, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .align(Alignment.CenterHorizontally)
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .background(Panel, RoundedCornerShape(18.dp))
                                    .border(
                                        width = 1.dp,
                                        color = PanelRaised,
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .padding(top = 5.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val navItems = listOf(
                                    Triple("⌂", "Главная", 0),
                                    Triple("♫", "Музыка", 1),
                                    Triple("▤", "Плейлисты", 2),
                                    Triple("≋", "Звук", 3),
                                    Triple("⚙", "Настройки", 4)
                                )
                                navItems.forEach { (symbol, label, index) ->
                                    val selected = selectedTab == index
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { navigateTo(tab = index) }
                                            .padding(vertical = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = symbol,
                                            color = if (selected) Accent else MutedText,
                                            fontSize = 21.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            text = label,
                                            color = if (selected) Accent else MutedText,
                                            fontSize = 9.sp,
                                            maxLines = 1
                                        )
                                        if (selected) {
                                            Spacer(Modifier.height(3.dp))
                                            Box(
                                                Modifier
                                                    .width(22.dp)
                                                    .height(2.dp)
                                                    .background(Accent, RoundedCornerShape(2.dp))
                                            )
                                        }
                                    }
                                }
                            }
                            if (playlistToRename != null) {
                                AlertDialog(
                                    onDismissRequest = { playlistToRename = null },
                                    title = { Text("Переименовать плейлист", color = TextPrimary) },
                                    text = {
                                        OutlinedTextField(
                                            value = renamePlaylistInput,
                                            onValueChange = { renamePlaylistInput = it },
                                            singleLine = true,
                                            label = { Text("Новое название") }
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            val oldName = playlistToRename
                                            val newName = renamePlaylistInput.trim()
                                            if (oldName != null && newName.isNotEmpty()) {
                                                if (newName == oldName || !playlists.containsKey(newName)) {
                                                    val updated = playlists.toMutableMap()
                                                    val ids = updated.remove(oldName).orEmpty()
                                                    updated[newName] = ids
                                                    playlists = updated
                                                    savePlaylists(updated)
                                                    if (selectedPlaylist == oldName) selectedPlaylist = newName
                                                } else {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "Плейлист с таким названием уже существует",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                            playlistToRename = null
                                        }) {
                                            Text("Сохранить", color = Accent)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { playlistToRename = null }) {
                                            Text("Отмена")
                                        }
                                    }
                                )
                            }

                            if (playlistToDelete != null) {
                                val nameToDelete = playlistToDelete
                                AlertDialog(
                                    onDismissRequest = { playlistToDelete = null },
                                    title = { Text("Удалить плейлист?", color = TextPrimary) },
                                    text = { Text("Плейлист «$nameToDelete» будет удалён. Музыкальные файлы останутся в библиотеке.") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            if (nameToDelete != null) {
                                                val updated = playlists.toMutableMap()
                                                updated.remove(nameToDelete)
                                                playlists = updated
                                                savePlaylists(updated)
                                                if (selectedPlaylist == nameToDelete) {
                                                    selectedPlaylist = null
                                                    libraryMode = "Композиции"
                                                }
                                            }
                                            playlistToDelete = null
                                        }) {
                                            Text("Удалить", color = Color(0xFFFF6B6B))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { playlistToDelete = null }) {
                                            Text("Отмена")
                                        }
                                    }
                                )
                            }

                            if (createPlaylistDialogOpen) {
                                AlertDialog(
                                    onDismissRequest = { createPlaylistDialogOpen = false },
                                    title = { Text("Новый плейлист", color = TextPrimary) },
                                    text = {
                                        OutlinedTextField(
                                            value = playlistNameInput,
                                            onValueChange = { playlistNameInput = it },
                                            singleLine = true,
                                            label = { Text("Название плейлиста") }
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            val name = playlistNameInput.trim()
                                            if (name.isNotEmpty()) {
                                                val updated = playlists.toMutableMap()
                                                if (!updated.containsKey(name)) {
                                                    updated[name] = emptyList()
                                                    playlists = updated
                                                    savePlaylists(updated)
                                                }
                                                navigateTo(tab = 1, mode = "Композиции", playlist = name, albumId = null, artist = null)
                                            }
                                            createPlaylistDialogOpen = false
                                        }) {
                                            Text("Создать", color = Accent)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { createPlaylistDialogOpen = false }) {
                                            Text("Отмена")
                                        }
                                    }
                                )
                            }

                            if (sleepTimerDialogOpen) {
                                AlertDialog(
                                    onDismissRequest = { sleepTimerDialogOpen = false },
                                    title = { Text("Таймер сна", color = TextPrimary) },
                                    text = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            listOf(15, 30, 45, 60, 90).forEach { minutes ->
                                                TextButton(
                                                    onClick = {
                                                        sleepTimerMinutes = minutes
                                                        startService(
                                                            Intent(this@MainActivity, PlaybackService::class.java)
                                                                .setAction(PlaybackService.ACTION_START_SLEEP_TIMER)
                                                                .putExtra(PlaybackService.EXTRA_SLEEP_TIMER_MINUTES, minutes)
                                                        )
                                                        sleepTimerDialogOpen = false
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text("$minutes минут", color = Accent)
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { sleepTimerDialogOpen = false }) {
                                            Text("ЗАКРЫТЬ", color = MutedText)
                                        }
                                    },
                                    containerColor = Panel
                                )
                            }

                            if (equalizerDialogOpen) {
                                AlertDialog(
                                    onDismissRequest = { equalizerDialogOpen = false },
                                    title = { Text("Эквалайзер", color = TextPrimary) },
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
                                                Text("Эквалайзер включён", color = TextPrimary)
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
                                                color = MutedText,
                                                fontSize = 12.sp
                                            )

                                            equalizerFrequencies.forEachIndexed { index, label ->
                                                Column {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(label, color = TextPrimary, fontSize = 12.sp)
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
                                            Text("Усиление баса", color = TextPrimary)
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
                                            Text("$bassStrength %", color = MutedText, fontSize = 12.sp)

                                            Text("Объёмное звучание", color = TextPrimary)
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
                                            Text("$virtualizerStrength %", color = MutedText, fontSize = 12.sp)

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


    private fun installCrashLogger() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val crashFile = java.io.File(filesDir, "last_crash.txt")
                crashFile.writeText(
                    "Pure Music Player crash log\n" +
                        "Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date())}\n" +
                        "Thread: ${thread.name}\n\n" +
                        error.stackTraceToString()
                )
            } catch (_: Exception) {
                // Never prevent Android's normal crash handling if logging fails.
            }
            previousHandler?.uncaughtException(thread, error)
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

        val intent = Intent(this, PlaybackService::class.java)
        intent.action = PlaybackService.ACTION_UPDATE_EQUALIZER
        intent.putExtra("eq_enabled", enabled)
        intent.putExtra("eq_levels", levels.toIntArray())
        intent.putExtra("bass_strength", bass)
        intent.putExtra("virtualizer_strength", virtualizer)
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

        // Keep the proven audio query minimal for better compatibility with
        // OEM MediaStore providers. Album names are loaded separately below.
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION
        )

        try {
            contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val artist = cursor.getString(artistColumn)
                        ?.takeUnless { it.isBlank() || it == "<unknown>" }
                        ?: "Неизвестный исполнитель"

                    result += Track(
                        id = id,
                        title = cursor.getString(titleColumn)
                            ?.takeUnless { it.isBlank() }
                            ?: "Без названия",
                        artist = artist,
                        uri = ContentUris.withAppendedId(collection, id),
                        albumId = cursor.getLong(albumColumn),
                        album = "Неизвестный альбом",
                        dateAdded = cursor.getLong(dateAddedColumn),
                        durationMs = cursor.getLong(durationColumn).coerceAtLeast(0L)
                    )
                }
            }
        } catch (error: Exception) {
            android.util.Log.e("PureMusicPlayer", "Не удалось прочитать MediaStore audio", error)
            return emptyList()
        }

        // Read album names in a separate query. If an OEM provider does not
        // expose the Albums collection correctly, tracks still remain visible.
        try {
            val albumNames = mutableMapOf<Long, String>()
            val albumProjection = arrayOf(
                MediaStore.Audio.Albums._ID,
                MediaStore.Audio.Albums.ALBUM
            )
            contentResolver.query(
                MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                albumProjection,
                null,
                null,
                "${MediaStore.Audio.Albums.ALBUM} ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
                while (cursor.moveToNext()) {
                    val albumId = cursor.getLong(idColumn)
                    val albumName = cursor.getString(nameColumn)
                        ?.takeUnless { it.isBlank() || it == "<unknown>" }
                        ?: "Неизвестный альбом"
                    albumNames[albumId] = albumName
                }
            }

            for (index in result.indices) {
                val track = result[index]
                result[index] = track.copy(
                    album = albumNames[track.albumId] ?: "Неизвестный альбом"
                )
            }
        } catch (error: Exception) {
            android.util.Log.w("PureMusicPlayer", "Не удалось прочитать названия альбомов", error)
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
            .height(30.dp)
    ) {
        val barCount = 54
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(1.dp.toPx())
        val maxHeight = size.height * 0.78f

        for (index in 0 until barCount) {
            val wave = abs(sin(phase + index * 0.38f)).toFloat()
            val detail = abs(sin(phase * 1.55f + index * 0.77f)).toFloat()
            val level = if (isPlaying) {
                0.10f + wave * 0.48f + detail * 0.20f
            } else {
                0.10f + abs(sin(index * 0.63f)).toFloat() * 0.12f
            }
            // During rotation Compose can briefly measure this Canvas at 0 px height.
            // Keep the clamp bounds valid even when maxHeight is smaller than 2.dp.
            val minBarHeight = minOf(2.dp.toPx(), maxHeight)
            val barHeight = (maxHeight * level).coerceIn(minBarHeight, maxHeight)
            val x = index * (barWidth + gap)
            val y = (size.height - barHeight) / 2f
            val alpha = if (isPlaying) 0.48f + wave * 0.38f else 0.34f

            drawRoundRect(
                color = Accent.copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}


@Composable
private fun AvMonogram(size: androidx.compose.ui.unit.Dp = 34.dp) {
    Box(
        modifier = Modifier.size(width = size * 1.35f, height = size),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "A",
            color = Accent,
            fontSize = (size.value * 0.88f).sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Light,
            modifier = Modifier.offset(x = (-size * 0.13f))
        )
        Text(
            text = "V",
            color = Accent.copy(alpha = 0.88f),
            fontSize = (size.value * 0.88f).sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Light,
            modifier = Modifier.offset(x = (size * 0.13f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.72f)
                .height(1.dp)
                .background(Accent.copy(alpha = 0.8f))
        )
    }
}
