package org.siloserver.silo.android.ui.components.marquee

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.siloserver.silo.android.R
import org.siloserver.silo.common.ui.marquee.MarqueeScrim
import org.siloserver.silo.common.ui.marquee.MarqueeScrimStyle

/**
 * A first-run screen on the phone: transparent over the shared backdrop, a
 * scrim for legibility, a top bar, and content anchored to the bottom like a
 * streaming app's welcome screen. The column scrolls, so large text and the
 * keyboard push the title up instead of clipping the controls.
 *
 * @param onBack what system Back does; null leaves Back to the navigation
 *   host. Pass null while a sign-in is in flight so leaving can't race it.
 * @param hero optional brand mark centred in the room above the content, which
 *   bottom-anchoring leaves by construction. It takes exactly what the form
 *   leaves it and shrinks to fit, so a raised keyboard makes it smaller rather
 *   than making it disappear.
 */
@Composable
fun MarqueeStage(
    modifier: Modifier = Modifier,
    scrim: MarqueeScrimStyle = MarqueeScrimStyle.Bottom,
    frostStart: Float? = 0.42f,
    maxWidth: Dp = 440.dp,
    onBack: (() -> Unit)? = null,
    blockBack: Boolean = false,
    topBar: @Composable RowScope.() -> Unit = {},
    hero: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = onBack != null || blockBack) { if (!blockBack) onBack?.invoke() }
    Box(modifier.fillMaxSize()) {
        MarqueeScrim(scrim, frostStart = frostStart)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = topBar,
            )
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
            ) {
                val viewport = maxHeight
                val density = LocalDensity.current
                // Height the form needs, measured rather than guessed, so the mark
                // can take exactly the room above it. Zero until the first layout,
                // which is also what a stage with room to spare looks like.
                var formHeight by remember { mutableStateOf(0.dp) }
                Column(
                    Modifier
                        .fillMaxSize()
                        // Keep the primary action in view when the room changes.
                        .verticalScroll(rememberScrollState(), reverseScrolling = true),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(
                        Modifier
                            .heightIn(min = viewport)
                            .widthIn(max = maxWidth)
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, bottom = StageBottomPadding),
                        // A hero and the form are the two ends of a first-run
                        // screen, so they share the height between them. Without
                        // one, content keeps its bottom-anchored look.
                        verticalArrangement = if (hero != null) {
                            Arrangement.SpaceBetween
                        } else {
                            Arrangement.Bottom
                        },
                    ) {
                        if (hero != null) {
                            // The mark keeps whatever the form leaves and shrinks
                            // to fit it: a raised keyboard should make the brand
                            // smaller, not make it disappear.
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(
                                        (viewport - formHeight - StageBottomPadding)
                                            .coerceAtLeast(0.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) { hero() }
                        }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .onSizeChanged { formHeight = with(density) { it.height.toDp() } },
                            content = content,
                        )
                    }
                }
            }
        }
    }
}

/** Padding under a first-run screen's content, shared with the hero's room. */
private val StageBottomPadding = 20.dp

/** The Silo wordmark for a first-run top bar. */
@Composable
fun MarqueeWordmark(width: Dp = 92.dp) {
    Image(
        painter = painterResource(R.drawable.silo_wordmark),
        contentDescription = "weflix",
        contentScale = ContentScale.Fit,
        modifier = Modifier.width(width),
    )
}

/** Pushes the trailing top-bar item to the end. */
@Composable
fun RowScope.MarqueeTopBarSpacer() = Spacer(Modifier.weight(1f))
