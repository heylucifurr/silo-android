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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import org.siloserver.silo.android.ui.theme.SiloPageBackground
import org.siloserver.silo.common.player.resolvePlaybackStreamUrl
import org.siloserver.silo.common.ui.components.ThumbhashImage
import org.siloserver.silo.common.ui.marquee.rememberReduceMotion
import org.siloserver.silo.model.section.ResolvedSection
import org.siloserver.silo.model.section.SectionItem
import org.siloserver.silo.model.section.comingSoonDateLabel
import org.siloserver.silo.model.section.comingSoonLabel
import org.siloserver.silo.model.section.heroArtworkThumbhash
import org.siloserver.silo.model.section.heroArtworkUrl
import org.siloserver.silo.model.section.isComingSoon
import org.siloserver.silo.network.TokenManager

/** How long one hero slide rests before the carousel advances. */
private const val HERO_AUTO_ADVANCE_MS = 8_000L

/** The card's side inset, its top-corner radius, and its widest allowed width. */
private val HERO_CARD_INSET = 16.dp
private val HERO_CARD_TOP_CORNER = 20.dp
private val HERO_CARD_MAX_WIDTH = 720.dp

/**
 * The card is width × 0.78 tall (~4:3), so it reads as a poster frame rather
 * than a 16:9 video box.
 */
private const val HERO_CARD_HEIGHT_RATIO = 0.78f

/** The card dissolves into the page over its bottom 45%. */
private const val HERO_FADE_FRACTION = 0.45f

/** Backdrop → trailer crossfade once the trailer's first frame is on screen. */
private const val HERO_TRAILER_FADE_MS = 300

/** Page-dot geometry: the active pill, an inactive dot, and an overflow edge dot. */
private val HERO_DOT_ACTIVE_WIDTH = 18.dp
private val HERO_DOT_WIDTH = 6.dp
private val HERO_DOT_OVERFLOW_WIDTH = 4.dp
private val HERO_DOT_HEIGHT = 6.dp
private const val HERO_DOT_ANIM_MS = 200

/**
 * Above this many slides the strip's edge dots shrink to [HERO_DOT_OVERFLOW_WIDTH]
 * to read as "more beyond". The spec fixes the size but not the threshold, so the
 * literal reading is taken with a threshold of 7.
 */
private const val HERO_DOTS_BEFORE_OVERFLOW = 7

/** Mute control: a 40dp rounded square inside a 48dp touch target. */
private val HERO_MUTE_INSET = 12.dp
private val HERO_MUTE_VISUAL = 40.dp
private val HERO_MUTE_TOUCH = 48.dp
private val HERO_MUTE_CORNER = 10.dp

/**
 * How far the card's bottom colour is carried into the page below it. The card
 * fades to the page's base colour, but the page also lays its own near-invisible
 * washes over that base, so the two meet a few levels apart. Feathering the card's
 * own colour into the page turns that step into a ramp nothing can see.
 */
private val HERO_EDGE_FEATHER = 20.dp

/** Type sizes the spec fixes: the chips, the text lines, and the title fallback. */
private val HERO_CHIP_TEXT_SIZE = 13.sp
private val HERO_META_TEXT_SIZE = 15.sp
private val HERO_TITLE_TEXT_SIZE = 24.sp

/** The card's title art cap, and the widest share of the card it may take. */
private val HERO_LOGO_MAX_HEIGHT = 64.dp
private const val HERO_LOGO_MAX_WIDTH_FRACTION = 0.6f

/** Content insets inside the card, and the gaps between the stacked blocks. */
private val HERO_CONTENT_SIDE = 16.dp
private val HERO_CONTENT_BOTTOM = 12.dp
private val HERO_LOGO_TO_META = 8.dp
private val HERO_META_TO_DATE = 6.dp
private val HERO_DATE_TO_DOTS = 14.dp

/** The white "Coming soon" chip and the grey classification chip. */
private val HERO_STATUS_CHIP_BG = Color.White
private val HERO_STATUS_CHIP_TEXT = Color.Black
private val HERO_RATING_CHIP_BG = Color(0xFF3A3A3A)
private val HERO_CHIP_CORNER = 4.dp
private val HERO_CHIP_PAD_H = 7.dp
private val HERO_CHIP_PAD_V = 3.dp

/** Secondary grey for the year, the genre and the inactive dots. */
private val HERO_MUTED_TEXT = Color.White.copy(alpha = 0.62f)
private val HERO_INACTIVE_DOT = Color.White.copy(alpha = 0.4f)

/**
 * The hero at the top of Home for a row the administrator flagged `featured`.
 *
 * The card is inset, has rounded top corners and square bottom corners, and is
 * ~4:3. Its backdrop and its trailer both fill it centre-cropped, and its bottom
 * fades into the page's own background so the card dissolves rather than ending
 * on an edge. The title art, the chips, the release date and the page dots all
 * sit inside that faded area, so they read as sitting on the page.
 *
 * There is no Play / More info button and no synopsis: on a phone the card is the
 * target. Tapping it opens the slide — the title page for a title the library
 * owns, the coming-soon page for one it does not.
 *
 * Nothing renders when the row carries no items, and a slide with no trailer (or
 * one that fails to play) keeps its backdrop with no error shown.
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
        modifier = modifier.fillMaxWidth(),
        // A tablet's card stops at HERO_CARD_MAX_WIDTH and centres.
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = HERO_CARD_INSET)
                .widthIn(max = HERO_CARD_MAX_WIDTH)
                .fillMaxWidth()
                .aspectRatio(1f / HERO_CARD_HEIGHT_RATIO)
                .clip(
                    RoundedCornerShape(
                        topStart = HERO_CARD_TOP_CORNER,
                        topEnd = HERO_CARD_TOP_CORNER,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp,
                    ),
                )
                .semantics {
                    contentDescription =
                        heroAccessibilityLabel(section.title, current, currentPage, slides.size)
                }
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
                HeroSlideMedia(
                    item = slides[page],
                    clipUrl = heroClipUrl(slides[page].heroVideoUrl, serverUrl),
                    // Only the slide on screen holds a decoder. Leaving it tears
                    // the player down, so the outgoing trailer stops and resets
                    // and the incoming one starts from its own first frame.
                    active = page == pagerState.currentPage,
                    trailerPlaying = advancing && page == pagerState.currentPage,
                )
            }

            // The card dissolves into the page: the gradient lands on the page's
            // own background colour, so the card ends where the page begins.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(HERO_FADE_FRACTION)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to SiloPageBackground,
                        ),
                    ),
            )

            HeroSlideContent(
                item = current,
                position = currentPage,
                count = slides.size,
                onSelectPage = { target -> scope.launch { pagerState.animateScrollToPage(target) } },
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }

        // Carry the card's bottom colour a little way into the page, so the last
        // few levels the page's own washes add never land on a hard edge.
        Box(
            modifier = Modifier
                .padding(horizontal = HERO_CARD_INSET)
                .widthIn(max = HERO_CARD_MAX_WIDTH)
                .fillMaxWidth()
                .height(HERO_EDGE_FEATHER)
                .background(
                    Brush.verticalGradient(
                        0f to SiloPageBackground,
                        1f to Color.Transparent,
                    ),
                ),
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
 * One slide's picture: the backdrop, then the muted trailer crossfading in over
 * it once the trailer has actually painted its first frame, and the mute control
 * on top.
 *
 * Both layers fill the card and centre-crop, so a letterboxed trailer shows no
 * black bars. A slide with no clip — or one whose clip fails — keeps the backdrop
 * alone and shows nothing about the failure.
 */
@Composable
private fun HeroSlideMedia(
    item: SectionItem,
    clipUrl: String?,
    /** Whether this is the slide on screen. Only it may hold a decoder. */
    active: Boolean,
    trailerPlaying: Boolean,
) {
    var clipFailed by remember(item.contentId) { mutableStateOf(false) }
    var clipMuted by remember(item.contentId) { mutableStateOf(true) }
    // The backdrop stays visible until the trailer has a frame to show, so the
    // card never flashes black while the clip buffers.
    var trailerPainted by remember(item.contentId) { mutableStateOf(false) }

    val trailerAlpha by animateFloatAsState(
        targetValue = if (trailerPainted) 1f else 0f,
        animationSpec = tween(HERO_TRAILER_FADE_MS),
        label = "heroTrailerAlpha",
    )

    val playableClipUrl = clipUrl?.takeIf { active && !clipFailed }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        ThumbhashImage(
            url = item.heroArtworkUrl,
            thumbhash = item.heroArtworkThumbhash,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            crossfadeMillis = 0,
        )
        if (playableClipUrl != null) {
            HeroTrailerLayer(
                clipUrl = playableClipUrl,
                playing = trailerPlaying,
                muted = clipMuted,
                onFirstFrame = { trailerPainted = true },
                onUnavailable = { clipFailed = true },
                modifier = Modifier.fillMaxSize().alpha(trailerAlpha),
            )
        }
        if (playableClipUrl != null && trailerPainted) {
            HeroMuteToggle(
                muted = clipMuted,
                onToggle = { clipMuted = !clipMuted },
                // The 48dp touch target centres the 40dp control, so an 8dp
                // padding puts the control 12dp from the card's top and right.
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(HERO_MUTE_INSET - (HERO_MUTE_TOUCH - HERO_MUTE_VISUAL) / 2),
            )
        }
    }
}

/**
 * The muted autoplaying clip. Nothing but the video: no controller, no chrome,
 * and zoomed to fill so a letterboxed trailer still covers the card.
 */
@OptIn(UnstableApi::class)
@Composable
private fun HeroTrailerLayer(
    clipUrl: String,
    playing: Boolean,
    muted: Boolean,
    onFirstFrame: () -> Unit,
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
            // The crossfade waits for a real frame, not merely a prepared player.
            override fun onRenderedFirstFrame() {
                onFirstFrame()
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
                // Zoom, not fit: a letterboxed clip fills the card.
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

/**
 * The mute control: a 40dp rounded square on a 48dp touch target, so the visible
 * control keeps the spec's inset while the target stays finger-sized.
 */
@Composable
private fun HeroMuteToggle(
    muted: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onToggle, modifier = modifier.size(HERO_MUTE_TOUCH)) {
        Box(
            modifier = Modifier
                .size(HERO_MUTE_VISUAL)
                .clip(RoundedCornerShape(HERO_MUTE_CORNER))
                .background(Color.Black.copy(alpha = 0.45f))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(HERO_MUTE_CORNER),
                ),
            contentAlignment = Alignment.Center,
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

/** The title text's subtle shadow, so it holds up over a bright frame. */
private val HeroTitleShadow = Shadow(
    color = Color.Black.copy(alpha = 0.6f),
    offset = Offset(0f, 1f),
    blurRadius = 6f,
)

/**
 * Everything stacked inside the card's faded foot: the title art, the chips and
 * metadata, the release date and the page dots. They sit on the fade rather than
 * in a box of their own, so the card reads as dissolving into the page.
 */
@Composable
private fun HeroSlideContent(
    item: SectionItem,
    position: Int,
    count: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = HERO_CONTENT_SIDE,
                end = HERO_CONTENT_SIDE,
                bottom = HERO_CONTENT_BOTTOM,
            ),
    ) {
        HeroCardTitle(item)
        Spacer(Modifier.height(HERO_LOGO_TO_META))
        HeroChipsRow(item)
        item.comingSoonDateLabel?.let { date ->
            Spacer(Modifier.height(HERO_META_TO_DATE))
            HeroDateRow(date)
        }
        Spacer(Modifier.height(HERO_DATE_TO_DOTS))
        HeroPageDots(
            position = position,
            count = count,
            onSelectPage = onSelectPage,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

/**
 * The title art at the card's bottom-left, over the fade — the server's clear
 * logo when it has one, the title as text otherwise. The art keeps its aspect
 * inside a 60%-of-card width and a [HERO_LOGO_MAX_HEIGHT] height, and a logo that
 * fails to load falls back to the text on the next frame.
 */
@Composable
private fun HeroCardTitle(item: SectionItem) {
    var logoFailed by remember(item.contentId) { mutableStateOf(false) }
    val logoUrl = item.logoUrl?.takeIf { it.isNotBlank() && !logoFailed }

    if (logoUrl != null) {
        ThumbhashImage(
            url = logoUrl,
            thumbhash = null,
            // The card itself is announced; the art is decorative here.
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth(HERO_LOGO_MAX_WIDTH_FRACTION)
                .heightIn(max = HERO_LOGO_MAX_HEIGHT),
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            transparent = true,
            onError = { logoFailed = true },
        )
    } else {
        Text(
            text = item.title,
            color = Color.White,
            fontSize = HERO_TITLE_TEXT_SIZE,
            lineHeight = HERO_TITLE_TEXT_SIZE * 1.2f,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = LocalTextStyle.current.copy(shadow = HeroTitleShadow),
            modifier = Modifier.fillMaxWidth(HERO_LOGO_MAX_WIDTH_FRACTION),
        )
    }
}

/**
 * The chips and the metadata, one line under the title: the white status chip, the
 * grey classification chip, then the runtime, the year and the first genre. The
 * parts are separated by spacing alone, as Prime's are.
 */
@Composable
private fun HeroChipsRow(item: SectionItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // A title the library owns offers Play on its page, so it carries no
        // status chip here.
        if (item.isComingSoon) HeroStatusChip("Coming soon")
        heroRatingChip(item)?.let { rating -> HeroRatingChip(rating) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            // The facts give way before the chips do on a narrow card.
            modifier = Modifier.weight(1f, fill = false),
        ) {
            heroMetadataParts(item).forEach { part ->
                Text(
                    text = part.label,
                    color = if (part.emphasised) Color.White else HERO_MUTED_TEXT,
                    fontSize = HERO_META_TEXT_SIZE,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The white status chip: black bold uppercase on a white pill. */
@Composable
private fun HeroStatusChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(HERO_CHIP_CORNER))
            .background(HERO_STATUS_CHIP_BG)
            .padding(horizontal = HERO_CHIP_PAD_H, vertical = HERO_CHIP_PAD_V),
    ) {
        Text(
            text = label.uppercase(),
            color = HERO_STATUS_CHIP_TEXT,
            fontSize = HERO_CHIP_TEXT_SIZE,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** The classification chip: white bold on grey. */
@Composable
private fun HeroRatingChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(HERO_CHIP_CORNER))
            .background(HERO_RATING_CHIP_BG)
            .padding(horizontal = HERO_CHIP_PAD_H, vertical = HERO_CHIP_PAD_V),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = HERO_CHIP_TEXT_SIZE,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** The release date on its own line, for a title the library does not own. */
@Composable
private fun HeroDateRow(date: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.CalendarToday,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = date,
            color = Color.White,
            fontSize = HERO_META_TEXT_SIZE,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One dot per slide. The active dot is a white pill, the rest are smaller grey
 * circles, and their widths animate as the carousel moves. Once the row holds
 * more slides than the strip shows, its edge dots shrink to read as "more
 * beyond".
 */
@Composable
private fun HeroPageDots(
    position: Int,
    count: Int,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return
    val overflows = count > HERO_DOTS_BEFORE_OVERFLOW
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { index ->
            val active = index == position
            val edge = overflows && (index == 0 || index == count - 1)
            val targetWidth = when {
                active -> HERO_DOT_ACTIVE_WIDTH
                edge -> HERO_DOT_OVERFLOW_WIDTH
                else -> HERO_DOT_WIDTH
            }
            val targetHeight = if (edge && !active) HERO_DOT_OVERFLOW_WIDTH else HERO_DOT_HEIGHT
            val width by animateDpAsState(
                targetValue = targetWidth,
                animationSpec = tween(HERO_DOT_ANIM_MS),
                label = "heroDotWidth",
            )
            val height by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = tween(HERO_DOT_ANIM_MS),
                label = "heroDotHeight",
            )
            Box(
                modifier = Modifier
                    .size(width = width, height = height)
                    .clip(CircleShape)
                    .background(if (active) Color.White else HERO_INACTIVE_DOT)
                    .semantics { contentDescription = "Go to item ${index + 1} of $count" }
                    .clickable(role = Role.Button) { onSelectPage(index) },
            )
        }
    }
}

/**
 * What a screen reader announces for the hero. The card is one target, so the
 * title, its facts and its availability are read together rather than as a set of
 * separate controls.
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
 * One fact in the hero's metadata row. Prime renders the runtime bright and the
 * year and genre dimmer beside it, so each part carries its own emphasis.
 */
internal data class HeroMetaPart(val label: String, val emphasised: Boolean)

/**
 * The facts the chips sit beside, from the card's own fields: the runtime, then
 * the year and the first genre. An episode card leads with its position instead of
 * the year. The classification is a chip, not a part.
 */
internal fun heroMetadataParts(item: SectionItem): List<HeroMetaPart> {
    val parts = mutableListOf<HeroMetaPart>()
    val isEpisode = item.type.equals("episode", ignoreCase = true)
    if (isEpisode && item.seasonNumber != null && item.episodeNumber != null) {
        parts += HeroMetaPart("S${item.seasonNumber} · E${item.episodeNumber}", emphasised = false)
    }
    heroRuntimeLabel(item.runtime)?.let { parts += HeroMetaPart(it, emphasised = true) }
    if (!isEpisode && item.year > 0) {
        parts += HeroMetaPart(item.year.toString(), emphasised = false)
    }
    if (!isEpisode) {
        item.genres.firstOrNull { it.isNotBlank() }?.let { parts += HeroMetaPart(it, emphasised = false) }
    }
    return parts
}

/** [heroMetadataParts] as the one line a screen reader or a test reads. */
internal fun heroMetadataLine(item: SectionItem): String =
    heroMetadataParts(item).joinToString(" · ") { it.label }

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








