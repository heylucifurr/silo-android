package org.siloserver.silo.android.ui.screens.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.siloserver.silo.common.player.resolvePlaybackStreamUrl
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.common.ui.marquee.rememberReduceMotion
import org.siloserver.silo.model.section.ResolvedSection
import org.siloserver.silo.model.section.SectionItem
import org.siloserver.silo.model.section.comingSoonLabel
import org.siloserver.silo.model.section.heroArtworkThumbhash
import org.siloserver.silo.model.section.heroArtworkUrl
import org.siloserver.silo.model.section.isComingSoon
import org.siloserver.silo.network.TokenManager

/** How long one hero slide rests before the carousel advances. */
private const val HERO_AUTO_ADVANCE_MS = 8_000L

/** The hero frame's baseline height, before it is scaled for large text. */
private val HERO_BASE_HEIGHT = 400.dp

/**
 * The full-width hero at the top of Home for a row the administrator flagged
 * `featured`: a swipeable carousel of large backdrops, each autoplaying the
 * title's muted trailer clip when the server has one, with page dots, a mute
 * toggle and an 8-second auto-advance.
 *
 * A curated "Most anticipated" slide the library does not own offers
 * "Coming soon · <date>" and More info instead of Play; a title that has since
 * landed in the library plays like any other item. Everything else about a
 * slide — artwork, title, metadata, overview — comes from the ordinary section
 * card, so a hero row of library items behaves exactly like the web hero.
 *
 * Nothing renders when the row carries no items, and a slide with no trailer
 * (or one that fails to play) keeps its backdrop with no error shown.
 */
@Composable
fun HomeHeroCarousel(
    section: ResolvedSection,
    /** Opens a slide that is not a coming-soon title. */
    onItemClick: (SectionItem) -> Unit,
    /** Opens the coming-soon detail page for a curated title the library lacks. */
    onComingSoonClick: (SectionItem) -> Unit,
    /** Plays a slide the library owns. */
    onPlayClick: (SectionItem) -> Unit,
    /** False while the hero is scrolled out of view: everything pauses. */
    isOnScreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val slides = remember(section.items) { section.items.filter { it.contentId.isNotBlank() } }
    if (slides.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { slides.size })
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val powerSaveMode = rememberPowerSaveMode()
    val appResumed = rememberAppResumed()

    // The server builds a hero clip URL as a site-relative path
    // (`/api/v2/artwork/...`), which a browser resolves against its own origin.
    // A player has no origin, so it is resolved against the active server here —
    // the same resolution a stream URL goes through. Until it is known, a slide
    // simply keeps its backdrop.
    val tokenManager: TokenManager = koinInject()
    val serverUrl by produceState(initialValue = null as String?) {
        value = runCatching { tokenManager.getServerUrl() }.getOrNull()
    }

    // A manual pause survives a page change, like the web hero's pause control.
    var userPaused by remember { mutableStateOf(false) }

    // Reduce motion, battery saver, the background and an off-screen hero all
    // stop the trailer and the auto-advance. None of them blocks a swipe.
    val autoplayAllowed = isOnScreen && appResumed && !powerSaveMode && !reduceMotion
    val advancing = autoplayAllowed && !userPaused && slides.size > 1
    val currentPage = pagerState.currentPage.coerceIn(0, slides.lastIndex)
    val current = slides[currentPage]

    // Auto-advance. Keyed on the page so a swipe restarts the dwell.
    LaunchedEffect(advancing, pagerState.currentPage, slides.size) {
        if (!advancing) return@LaunchedEffect
        delay(HERO_AUTO_ADVANCE_MS)
        pagerState.animateScrollToPage((pagerState.currentPage + 1) % slides.size)
    }

    // A section that shrinks under the pager (a profile switch, a realtime
    // update) must not leave the current page out of range.
    LaunchedEffect(slides.size) {
        if (pagerState.currentPage > slides.lastIndex) {
            pagerState.scrollToPage(slides.lastIndex.coerceAtLeast(0))
        }
    }

    // Large text grows the frame so the title block never crowds the artwork;
    // a landscape phone has far less room, so it gets the compact frame.
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val configuration = LocalConfiguration.current
    val heroHeight =
        if (configuration.screenHeightDp in 1..520) 240.dp else HERO_BASE_HEIGHT * fontScale

    val positionLabel = "No. " + (currentPage + 1).toString().padStart(2, '0')

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = heroHeight)
            .semantics {
                contentDescription =
                    "Featured, ${section.title}. Item ${currentPage + 1} of ${slides.size}."
            },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(heroHeight),
            pageSpacing = 0.dp,
        ) { page ->
            HeroSlideBackdrop(
                item = slides[page],
                clipUrl = heroClipUrl(slides[page].heroVideoUrl, serverUrl),
                // Only the visible slide holds a player: one decoder, one clip.
                // Pausing keeps it; only leaving the slide tears it down.
                active = page == pagerState.currentPage,
                trailerPlaying = advancing && page == pagerState.currentPage,
            )
        }

        HeroSlideContent(
            item = current,
            positionLabel = positionLabel,
            position = currentPage,
            count = slides.size,
            userPaused = userPaused,
            showPauseControl = autoplayAllowed && slides.size > 1,
            onTogglePause = { userPaused = !userPaused },
            onItemClick = { onItemClick(current) },
            onComingSoonClick = { onComingSoonClick(current) },
            onPlayClick = { onPlayClick(current) },
            onSelectPage = { target -> scope.launch { pagerState.animateScrollToPage(target) } },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Whether the app is in the foreground; false pauses the trailer and the dwell. */
@Composable
private fun rememberAppResumed(): Boolean {
    val owner = LocalLifecycleOwner.current
    var resumed by remember {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return resumed
}

/** Battery saver / low-power mode, tracked as it changes. */
@Composable
private fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    var enabled by remember(context) { mutableStateOf(context.isPowerSaveMode()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                enabled = context.isPowerSaveMode()
            }
        }
        runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return enabled
}

private fun Context.isPowerSaveMode(): Boolean =
    (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true

/**
 * One slide's artwork: the backdrop first, then the muted trailer clip once it
 * is ready. A slide with no clip — or one whose clip fails — keeps the backdrop
 * alone and shows nothing about the failure.
 */
@Composable
private fun HeroSlideBackdrop(
    item: SectionItem,
    clipUrl: String?,
    /** Whether this is the slide on screen. Only it may hold a decoder. */
    active: Boolean,
    trailerPlaying: Boolean,
) {
    var clipFailed by remember(item.contentId) { mutableStateOf(false) }
    var clipMuted by remember(item.contentId) { mutableStateOf(true) }
    var clipReady by remember(item.contentId) { mutableStateOf(false) }

    val playableClipUrl = clipUrl?.takeIf { active && !clipFailed }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        ThumbhashImage(
            url = item.heroArtworkUrl,
            thumbhash = item.heroArtworkThumbhash,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            crossfadeMillis = 0,
        )
        if (playableClipUrl != null) {
            HeroTrailerLayer(
                clipUrl = playableClipUrl,
                playing = trailerPlaying,
                muted = clipMuted,
                onReady = { clipReady = true },
                onUnavailable = { clipFailed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Scrims: the title block sits on the darkest part of the frame, and the
        // chrome above it stays readable over a bright still.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.45f to Color.Black.copy(alpha = 0.15f),
                        1f to Color.Black.copy(alpha = 0.92f),
                    ),
                ),
        )
        if (playableClipUrl != null && clipReady) {
            // Mute floats over the clip only; a backdrop has no sound.
            MuteToggle(
                muted = clipMuted,
                onToggle = { clipMuted = !clipMuted },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
        }
    }
}

/**
 * The clip URL a player can actually open: an absolute one passes through, and a
 * site-relative one (`/api/v2/artwork/...`, which the server sends) is resolved
 * against [serverUrl] exactly as a playback stream URL is. Null when there is no
 * clip, or when the server address is not known yet — the slide keeps its
 * backdrop either way.
 */
internal fun heroClipUrl(rawClipUrl: String?, serverUrl: String?): String? {
    val url = rawClipUrl?.trim().orEmpty()
    if (url.isEmpty()) return null
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    val base = serverUrl?.trim().orEmpty()
    if (base.isEmpty()) return null
    return resolvePlaybackStreamUrl(base, url)
}

/** The muted autoplaying clip. Nothing but the video: no controller, no chrome. */
@OptIn(UnstableApi::class)
@Composable
private fun HeroTrailerLayer(
    clipUrl: String,
    playing: Boolean,
    muted: Boolean,
    onReady: () -> Unit,
    onUnavailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(clipUrl) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 0f
            setMediaItem(MediaItem.fromUri(clipUrl))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) onReady()
            }

            override fun onPlayerError(error: PlaybackException) {
                onUnavailable()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player, playing) {
        if (playing) player.play() else player.pause()
    }
    LaunchedEffect(player, muted) {
        player.volume = if (muted) 0f else 1f
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                this.player = player
            }
        },
        update = { view -> view.player = player },
        modifier = modifier,
    )
}

@Composable
private fun MuteToggle(
    muted: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        Icon(
            imageVector = if (muted) {
                Icons.AutoMirrored.Outlined.VolumeOff
            } else {
                Icons.AutoMirrored.Outlined.VolumeUp
            },
            contentDescription = if (muted) "Unmute trailer" else "Mute trailer",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The slide's title block: the eyebrow, the title art (or the title text), the
 * metadata line, the overview, the actions and the page dots.
 */
@Composable
private fun HeroSlideContent(
    item: SectionItem,
    positionLabel: String,
    position: Int,
    count: Int,
    userPaused: Boolean,
    showPauseControl: Boolean,
    onTogglePause: () -> Unit,
    onItemClick: () -> Unit,
    onComingSoonClick: () -> Unit,
    onPlayClick: () -> Unit,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val comingSoon = item.isComingSoon
    val metadata = heroMetadataLine(item)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "FEATURED — $positionLabel",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.75f),
            fontWeight = FontWeight.SemiBold,
        )

        HeroTitle(item = item)

        if (metadata.isNotEmpty()) {
            Text(
                text = metadata,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.88f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
            Text(
                text = overview,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (comingSoon) {
                ComingSoonBadge(item.comingSoonLabel)
            } else {
                Button(
                    onClick = onPlayClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Play")
                }
            }
            Button(
                onClick = if (comingSoon) onComingSoonClick else onItemClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.18f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("More info")
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HeroPageDots(position = position, count = count, onSelectPage = onSelectPage)
            if (showPauseControl) {
                IconButton(onClick = onTogglePause, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (userPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (userPaused) "Resume slideshow" else "Pause slideshow",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** The title art when the server sent one, else the title as text. */
@Composable
private fun HeroTitle(item: SectionItem) {
    var logoFailed by remember(item.contentId) { mutableStateOf(false) }
    val logoUrl = item.logoUrl?.takeIf { it.isNotBlank() && !logoFailed }

    if (logoUrl != null) {
        ThumbhashImage(
            url = logoUrl,
            thumbhash = null,
            contentDescription = item.title,
            modifier = Modifier.heightIn(min = 44.dp, max = 96.dp).width(240.dp),
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            transparent = true,
            onError = { logoFailed = true },
        )
    } else {
        Text(
            text = item.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A non-interactive "Coming soon · <date>" pill, matching the web hero's badge. */
@Composable
private fun ComingSoonBadge(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.18f))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** One dot per slide; the active dot is wider. Each is a labelled control. */
@Composable
private fun HeroPageDots(
    position: Int,
    count: Int,
    onSelectPage: (Int) -> Unit,
) {
    if (count <= 1) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { index ->
            val active = index == position
            Box(
                modifier = Modifier
                    .size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (active) 0.95f else 0.45f))
                    .semantics {
                        contentDescription = "Go to item ${index + 1} of $count"
                        role = Role.Button
                    }
                    .clickable { onSelectPage(index) },
            )
        }
    }
}

/**
 * `2026 · 1h 40m · Action · PG-13` — the same facts the web hero's metadata line
 * shows, from the card's own fields. An episode card leads with its position
 * instead of the year.
 */
internal fun heroMetadataLine(item: SectionItem): String {
    val parts = mutableListOf<String>()
    val isEpisode = item.type.equals("episode", ignoreCase = true)
    if (isEpisode && item.seasonNumber != null && item.episodeNumber != null) {
        parts += "S${item.seasonNumber} · E${item.episodeNumber}"
    } else if (item.year > 0) {
        parts += item.year.toString()
    }
    heroRuntimeLabel(item.runtime)?.let { parts += it }
    if (!isEpisode) {
        item.genres.firstOrNull { it.isNotBlank() }?.let { parts += it }
    }
    item.contentRating?.trim()?.takeIf { it.isNotEmpty() }?.let { parts += it.uppercase() }
    return parts.joinToString(" · ")
}

/** `1h 40m` from a minute count; null when the card carries no runtime. */
internal fun heroRuntimeLabel(runtimeMinutes: Int?): String? {
    val minutes = runtimeMinutes ?: return null
    if (minutes <= 0) return null
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (remainder == 0) "${hours}h" else "${hours}h ${remainder}m"
}
