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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.outlined.Schedule
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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

/** The card's side inset from the page edge, and its corner radius. */
private val HERO_CARD_INSET = 16.dp
private val HERO_CARD_CORNER = 20.dp

/** The card is a 16:9 frame, like a still from the title. */
private const val HERO_CARD_ASPECT = 16f / 9f

/**
 * The hero at the top of Home for a row the administrator flagged `featured`,
 * laid out the way Prime Video's mobile hero is: an inset, rounded 16:9 card
 * holding the backdrop with the title's muted trailer playing over it, the
 * title art at its bottom-left, a mute toggle at its top-right, and the
 * metadata, availability line and page dots beneath it.
 *
 * There is no Play / More info button and no synopsis: on a phone the card
 * itself is the target. Tapping anywhere on it opens the slide — the title page
 * for a title the library owns, the coming-soon page for one it does not. A
 * curated "Most anticipated" slide the library lacks carries a
 * "Coming soon · <date>" line in place of a call to action.
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

    // Reduce motion, battery saver, the background and an off-screen hero all
    // stop the trailer and the auto-advance. None of them blocks a swipe.
    val advancing = isOnScreen && appResumed && !powerSaveMode && !reduceMotion && slides.size > 1
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    heroAccessibilityLabel(section.title, current, currentPage, slides.size)
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = HERO_CARD_INSET)
                .fillMaxWidth()
                .aspectRatio(HERO_CARD_ASPECT)
                .clip(RoundedCornerShape(HERO_CARD_CORNER))
                // A tap, not a drag: `clickable` leaves the pager's horizontal
                // swipe alone, so the card still pages between slides.
                .clickable(role = Role.Button) {
                    if (current.isComingSoon) onComingSoonClick(current) else onItemClick(current)
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 0.dp,
            ) { page ->
                HeroSlideBackdrop(
                    item = slides[page],
                    clipUrl = heroClipUrl(slides[page].heroVideoUrl, serverUrl),
                    // Only the visible slide holds a player: one decoder, one clip.
                    // Leaving the slide tears it down; pausing does not.
                    active = page == pagerState.currentPage,
                    trailerPlaying = advancing && page == pagerState.currentPage,
                )
            }
        }

        HeroSlideMeta(
            item = current,
            position = currentPage,
            count = slides.size,
            onSelectPage = { target -> scope.launch { pagerState.animateScrollToPage(target) } },
            modifier = Modifier.padding(horizontal = HERO_CARD_INSET),
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
 * One slide inside the card: the backdrop, then the muted trailer once it is
 * ready, the title art bottom-left and the mute toggle top-right.
 *
 * A slide with no clip — or one whose clip fails — keeps the backdrop alone and
 * shows nothing about the failure, and its mute control never appears.
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
        // A bottom scrim only: the title sits at the card's bottom-left and the
        // card is already inset and rounded, so no full-frame wash is needed.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                ),
        )
        if (playableClipUrl != null && clipReady) {
            // Mute floats over the clip only; a backdrop has no sound.
            MuteToggle(
                muted = clipMuted,
                onToggle = { clipMuted = !clipMuted },
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
            )
        }
        HeroCardTitle(
            item = item,
            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
        )
    }
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

/**
 * The title art at the card's bottom-left, over the artwork — the server's logo
 * when it has one, the title as text otherwise. A logo that fails to load falls
 * back to the text on the next frame.
 */
@Composable
private fun HeroCardTitle(item: SectionItem, modifier: Modifier = Modifier) {
    var logoFailed by remember(item.contentId) { mutableStateOf(false) }
    val logoUrl = item.logoUrl?.takeIf { it.isNotBlank() && !logoFailed }

    if (logoUrl != null) {
        ThumbhashImage(
            url = logoUrl,
            thumbhash = null,
            // The card itself is announced; the art is decorative here.
            contentDescription = null,
            modifier = modifier.heightIn(min = 32.dp, max = 76.dp).width(210.dp),
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            transparent = true,
            onError = { logoFailed = true },
        )
    } else {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}

/**
 * What sits under the card, as Prime's mobile hero has it: one line of chips and
 * metadata, then the availability line for a title the library does not own, then
 * the page dots. There is no synopsis and no button — the card above is the
 * target.
 */
@Composable
private fun HeroSlideMeta(
    item: SectionItem,
    position: Int,
    count: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            heroRatingChip(item)?.let { rating -> HeroChip(rating) }
            val metadata = heroMetadataLine(item)
            if (metadata.isNotEmpty()) {
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (item.isComingSoon) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = item.comingSoonLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        HeroPageDots(
            position = position,
            count = count,
            onSelectPage = onSelectPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 2.dp),
        )
    }
}

/** A classification pill, like the rating chip on Prime's hero. */
@Composable
private fun HeroChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
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
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { index ->
            val active = index == position
            Box(
                modifier = Modifier
                    .size(width = if (active) 18.dp else 6.dp, height = 6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (active) 0.95f else 0.4f))
                    .semantics { contentDescription = "Go to item ${index + 1} of $count" }
                    .clickable(role = Role.Button) { onSelectPage(index) },
            )
        }
    }
}

/**
 * What a screen reader announces for the hero. The card is one target, so the
 * title, its facts and its availability are read together rather than as a set
 * of separate controls.
 */
internal fun heroAccessibilityLabel(
    rowTitle: String,
    item: SectionItem,
    position: Int,
    count: Int,
): String {
    val parts = mutableListOf(rowTitle, item.title)
    heroMetadataLine(item).takeIf { it.isNotEmpty() }?.let { parts += it }
    if (item.isComingSoon) parts += item.comingSoonLabel
    parts += "Item ${position + 1} of $count"
    return parts.joinToString(". ")
}

/**
 * `1h 40m · 2026 · Action` — the facts Prime's hero puts beside its rating chip,
 * from the card's own fields. An episode card leads with its position instead of
 * the year, and the classification is the chip, not this line.
 */
internal fun heroMetadataLine(item: SectionItem): String {
    val parts = mutableListOf<String>()
    val isEpisode = item.type.equals("episode", ignoreCase = true)
    if (isEpisode && item.seasonNumber != null && item.episodeNumber != null) {
        parts += "S${item.seasonNumber} · E${item.episodeNumber}"
    }
    heroRuntimeLabel(item.runtime)?.let { parts += it }
    if (!isEpisode && item.year > 0) parts += item.year.toString()
    if (!isEpisode) item.genres.firstOrNull { it.isNotBlank() }?.let { parts += it }
    return parts.joinToString(" · ")
}

/** The classification chip beside [heroMetadataLine]; null when the card carries none. */
internal fun heroRatingChip(item: SectionItem): String? =
    item.contentRating?.trim()?.takeIf { it.isNotEmpty() }?.uppercase()

/** `1h 40m` from a minute count; null when the card carries no runtime. */
internal fun heroRuntimeLabel(runtimeMinutes: Int?): String? {
    val minutes = runtimeMinutes ?: return null
    if (minutes <= 0) return null
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (remainder == 0) "${hours}h" else "${hours}h ${remainder}m"
}





