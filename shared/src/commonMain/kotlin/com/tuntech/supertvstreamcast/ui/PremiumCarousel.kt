package com.tuntech.supertvstreamcast.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.theme.LocalReducedMotion
import com.tuntech.supertvstreamcast.theme.TvColors
import com.tuntech.supertvstreamcast.theme.TvDimens
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import shared.resources.*

/** One slide of the Home premium banner: text-free night artwork with its headline and line. */
private enum class PromoSlide(val key: String, val art: DrawableResource, val title: StringResource, val body: StringResource) {
    SPORTS("sports", Res.drawable.art_promo_sports, Res.string.promo_sports_title, Res.string.promo_sports_body),
    NO_ADS("no_ads", Res.drawable.art_promo_noads, Res.string.promo_noads_title, Res.string.promo_noads_body),
    REMOTE("remote", Res.drawable.art_promo_remote, Res.string.promo_remote_title, Res.string.promo_remote_body),
    MOVIES("movies", Res.drawable.art_promo_movies, Res.string.promo_movies_title, Res.string.promo_movies_body),
}

private const val SlideMillis = 4200L
/** Pages repeat this many times so the auto-advance and swipes wrap without a jump back. */
private const val Laps = 1000

// The artwork is a night scene in both themes, so its copy stays light.
private val PromoInk = Color.White
private val PromoNight = Color(0xFF12062B)

/**
 * The Home hero for free users: premium slides that advance on their own and pause while the user
 * swipes. A tap anywhere opens the paywall with the slide that was showing.
 */
@Composable internal fun PremiumCarousel(onPremium: (String) -> Unit, modifier: Modifier = Modifier) {
    val slides = PromoSlide.entries
    val pager = rememberPagerState(initialPage = slides.size * Laps / 2) { slides.size * Laps }
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    val reduced = LocalReducedMotion.current
    LaunchedEffect(dragged, reduced) {
        if (dragged || reduced) return@LaunchedEffect
        while (true) {
            delay(SlideMillis)
            pager.animateScrollToPage(pager.currentPage + 1, animationSpec = tween(700))
        }
    }
    val current = slides[pager.currentPage % slides.size]
    val shape = RoundedCornerShape(TvDimens.RadiusXl)
    val cta = stringResource(Res.string.promo_cta)
    val title = stringResource(current.title)
    Box(modifier.fillMaxWidth().height(300.dp).clip(shape).border(1.5.dp, TvColors.PremiumGradient, shape)
        .clickable(role = Role.Button) { onPremium(current.key) }
        .semantics { contentDescription = "$title. $cta" }) {
        HorizontalPager(pager, Modifier.matchParentSize(), key = { it }) { page ->
            PromoPage(slides[page % slides.size])
        }
        Row(Modifier.align(Alignment.TopStart).padding(18.dp).clip(RoundedCornerShape(100.dp)).background(TvColors.PremiumGradient).shimmer()
            .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GlyphIcon(Glyph.CROWN, Modifier.size(16.dp), TvColors.OnAccent)
            Text(stringResource(Res.string.promo_badge), style = MaterialTheme.typography.labelSmall, color = TvColors.OnAccent)
        }
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.clip(RoundedCornerShape(100.dp)).background(TvColors.PremiumGradient).shimmer()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(cta, style = MaterialTheme.typography.labelLarge, color = TvColors.OnAccent, maxLines = 1)
                Box(Modifier.size(24.dp).background(TvColors.OnAccent.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.RIGHT, Modifier.size(14.dp), TvColors.OnAccent)
                }
            }
            PageDots(slides.size, pager.currentPage % slides.size)
        }
    }
}

@Composable private fun PromoPage(slide: PromoSlide) {
    val drift = rememberPulse(14000)
    Box(Modifier.fillMaxSize().background(PromoNight)) {
        // The subject sits on the right of every artwork; the calm left side carries the copy.
        Image(painterResource(slide.art), null, Modifier.fillMaxSize().graphicsLayer { val zoom = 1.02f + 0.05f * drift.value; scaleX = zoom; scaleY = zoom },
            contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd)
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to PromoNight.copy(alpha = 0.92f), 0.5f to PromoNight.copy(alpha = 0.55f), 0.8f to Color.Transparent)))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to PromoNight.copy(alpha = 0.85f))))
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.66f).padding(start = 18.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(slide.title), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = PromoInk, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(stringResource(slide.body), style = MaterialTheme.typography.bodyMedium, color = PromoInk.copy(alpha = 0.85f),
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The selected slide's dot stretches into a gold pill. */
@Composable private fun PageDots(count: Int, selected: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == selected) 18.dp else 6.dp, label = "promoDot")
            Box(Modifier.size(width = width, height = 6.dp).clip(CircleShape)
                .background(if (i == selected) TvColors.PremiumGradient else Brush.linearGradient(listOf(PromoInk.copy(alpha = 0.45f), PromoInk.copy(alpha = 0.45f)))))
        }
    }
}
