package com.tuntech.supertvstreamcast.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.theme.*
import org.jetbrains.compose.resources.*
import shared.resources.*

internal fun Feature.label() = when(this) {
    Feature.HOME -> Res.string.home; Feature.REMOTE -> Res.string.remote
    Feature.MIRROR -> Res.string.mirror; Feature.IPTV -> Res.string.iptv; Feature.SETTINGS -> Res.string.settings
}
internal fun Feature.navLabel() = when(this) {
    Feature.HOME -> Res.string.nav_home; Feature.REMOTE -> Res.string.nav_remote
    Feature.MIRROR -> Res.string.nav_mirror; Feature.IPTV -> Res.string.nav_iptv; Feature.SETTINGS -> Res.string.nav_settings
}
internal fun UiError.label() = when(this) {
    UiError.CONNECTION -> Res.string.connection_failed; UiError.COMMAND -> Res.string.command_failed
    UiError.PAIRING_CODE -> Res.string.pairing_code_error; UiError.WAKE -> Res.string.wake_error
    UiError.PLAYLIST -> Res.string.playlist_error; UiError.INVALID_IP -> Res.string.invalid_ip
    UiError.PERMISSION -> Res.string.lan_denied; UiError.PLAYER -> Res.string.player_error
    UiError.MIRROR -> Res.string.mirror_unavailable; UiError.NO_WIFI -> Res.string.no_wifi
    UiError.APPS -> Res.string.apps_error; UiError.XTREAM_INPUT -> Res.string.xtream_input_error
    UiError.XTREAM -> Res.string.xtream_error; UiError.XTREAM_AUTH -> Res.string.xtream_auth_error
    UiError.FILE -> Res.string.file_error; UiError.STREAM_URL -> Res.string.stream_error
    UiError.GUIDE -> Res.string.guide_error; UiError.DOWNLOAD -> Res.string.download_error
    UiError.GUIDE_NO_CHANNELS -> Res.string.source_guide_locked
}
@Composable internal fun PrimaryCta(text: String, enabled: Boolean=true, glyph: Glyph?=Glyph.RIGHT, onClick: ()->Unit) {
    val source=remember {MutableInteractionSource()}
    val shape=RoundedCornerShape(TvDimens.RadiusMd)
    Button(onClick=onClick,enabled=enabled,shape=shape,interactionSource=source,
        contentPadding=PaddingValues(0.dp),colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,disabledContainerColor=TvColors.Raised),
        modifier=Modifier.fillMaxWidth().heightIn(min=TvDimens.Touch).pressScale(source,0.97f)
            .then(if(enabled) Modifier.shadow(16.dp,shape,ambientColor=TvColors.Pink,spotColor=TvColors.Pink) else Modifier)) {
        Row(Modifier.fillMaxWidth().background(if(enabled) TvColors.Gradient else Brush.horizontalGradient(listOf(TvColors.Raised,TvColors.Raised)))
            .shimmer(enabled).padding(horizontal=20.dp,vertical=17.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(text,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,color=if(enabled) TvColors.OnAccent else TvColors.Muted)
            if(glyph!=null) GlyphIcon(glyph,color=if(enabled) TvColors.OnAccent else TvColors.Muted)
        }
    }
}
@Composable internal fun CinemaPanel(modifier: Modifier=Modifier, content: @Composable ColumnScope.()->Unit) {
    Column(modifier.clip(RoundedCornerShape(TvDimens.Radius)).background(TvColors.SurfaceGradient)
        .border(1.dp,TvColors.Outline.copy(alpha=0.7f),RoundedCornerShape(TvDimens.Radius)).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}
/** A feature's glossy tile: its own gradient with the duotone icon on top. [accent] tints the edge. */
@Composable internal fun IconBubble(feature: Feature, accent: Color=TvColors.featureAccent(feature), size: androidx.compose.ui.unit.Dp=48.dp) {
    val shape=RoundedCornerShape(size*0.34f)
    Box(Modifier.size(size).shadow(8.dp,shape,ambientColor=accent,spotColor=accent).clip(shape).background(TvColors.featureGradient(feature))
        .drawBehind{drawCircle(Color.White.copy(alpha=0.22f),this.size.width*0.6f,androidx.compose.ui.geometry.Offset(this.size.width*0.2f,0f))},contentAlignment=Alignment.Center) {
        FeatureIcon(feature,Modifier.size(size*0.54f),TvColors.OnAccent)
    }
}
@Composable internal fun RoundAction(label: String, glyph: Glyph, modifier: Modifier=Modifier, enabled: Boolean=true, accent: Color=TvColors.Text, onClick: ()->Unit) {
    val source=remember {MutableInteractionSource()}
    IconButton(onClick=onClick,enabled=enabled,interactionSource=source,modifier=modifier.size(TvDimens.IconButton).pressScale(source).clip(CircleShape)
        .background(TvColors.Raised).border(1.dp,TvColors.Outline,CircleShape).semantics{contentDescription=label}) {
        GlyphIcon(glyph,color=if(enabled) accent else TvColors.Muted.copy(alpha=0.45f))
    }
}
@Composable internal fun Heading(title: String, body: String?=null) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        // Page titles carry the brand gradient.
        Text(title,style=MaterialTheme.typography.headlineLarge.copy(brush=TvColors.TitleGradient))
        if(body!=null) Text(body,style=MaterialTheme.typography.bodyLarge,color=TvColors.Muted)
    }
}
@Composable internal fun Artwork(modifier: Modifier=Modifier, resource: DrawableResource=TvArt.home) {
    val shape=RoundedCornerShape(TvDimens.Radius)
    Crossfade(resource,modifier.fillMaxWidth().clip(shape).border(1.dp,TvColors.Outline.copy(alpha=0.6f),shape),tween(TvMotion.Screen),label="artwork") {art ->
        Image(painterResource(art),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
    }
}
@Composable internal fun Scene(title: String, body: String, badge: String, modifier: Modifier=Modifier) {
    val shape=RoundedCornerShape(TvDimens.RadiusXl)
    // The picture drifts very slowly so the hero never looks frozen.
    val drift=rememberPulse(18000)
    Box(modifier.fillMaxWidth().heightIn(min=300.dp).shadow(20.dp,shape,ambientColor=TvColors.Pink,spotColor=TvColors.Cyan)
        .clip(shape).border(1.5.dp,TvColors.Gradient,shape)) {
        Crossfade(TvArt.home,Modifier.matchParentSize(),tween(TvMotion.Screen),label="heroArt") {art ->
            Image(painterResource(art),null,Modifier.fillMaxSize().graphicsLayer{val zoom=1.04f+0.06f*drift.value;scaleX=zoom;scaleY=zoom},
                contentScale=ContentScale.Crop,alignment=Alignment.TopCenter)
        }
        Box(Modifier.matchParentSize().background(TvColors.HeroScrim))
        Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(104.dp)) {
            Text(badge,Modifier.clip(RoundedCornerShape(100.dp)).background(TvColors.Gradient).padding(horizontal=12.dp,vertical=7.dp),
                style=MaterialTheme.typography.labelSmall,color=TvColors.OnAccent)
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(title,style=MaterialTheme.typography.displaySmall,color=TvColors.Text)
                Text(body,style=MaterialTheme.typography.bodyMedium,color=TvColors.Muted)
            }
        }
    }
}
@Composable internal fun AppHeader(brand: TvBrand, onSettings: ()->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        BrandMark(Modifier.size(42.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(Res.string.app_name),style=MaterialTheme.typography.titleLarge)
            Text(stringResource(Res.string.home_eyebrow),style=MaterialTheme.typography.labelSmall,color=TvColors.Muted)
        }
        Box(Modifier.clip(RoundedCornerShape(100.dp)).background(TvColors.Raised).border(1.dp,TvColors.Outline,RoundedCornerShape(100.dp))
            .clickable(role=Role.Button,onClick=onSettings).padding(12.dp),contentAlignment=Alignment.Center) {
            Text(brand.title.substringBefore(" / ").substringBefore(" BRAVIA"),style=MaterialTheme.typography.labelLarge,color=TvColors.Cyan,maxLines=1)
        }
    }
}
/**
 * A floating capsule. One gradient pill glides to the selected tab, which widens to show its
 * label; the other tabs stay as quiet icons.
 *
 * Every moving part follows the same per-tab fraction (0 = idle, 1 = selected), and that fraction
 * is only read while measuring, placing and drawing, so a tab change never recomposes the bar.
 */
@Composable internal fun FloatingDock(tab: Feature, onTab: (Feature)->Unit) {
    val entries=Feature.entries
    val reduced=LocalReducedMotion.current
    val shape=RoundedCornerShape(34.dp)
    val transition=updateTransition(tab,label="dock")
    // Critically damped: the pill settles without overshoot or wobble.
    val fractions=entries.map {feature ->
        transition.animateFloat({if(reduced) snap() else spring(dampingRatio=1f,stiffness=260f,visibilityThreshold=0.0005f)},label="dockFraction") {if(it==feature) 1f else 0f}
    }
    val stops=entries.map {TvColors.featureStops(it)}
    val lefts=remember {FloatArray(entries.size)}
    val widths=remember {FloatArray(entries.size)}
    Layout(content={
        entries.forEachIndexed {index,feature -> DockItem(feature,tab==feature,fractions[index]) {onTab(feature)}}
    },modifier=Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp,vertical=10.dp)
        .shadow(22.dp,shape,ambientColor=TvColors.Cyan,spotColor=TvColors.Pink).clip(shape).background(TvColors.Surface)
        .border(1.dp,TvColors.Outline.copy(alpha=0.7f),shape).padding(7.dp)
        .drawBehind {
            // The pill is the fraction-weighted blend of the tab slots, so it travels and resizes in one motion.
            var left=0f;var width=0f;var total=0f
            var start=Color.Transparent;var end=Color.Transparent
            entries.indices.forEach {index ->
                val f=fractions[index].value.coerceIn(0f,1f)
                if(f>0f) {
                    start=if(total==0f) stops[index].first() else lerp(start,stops[index].first(),f/(total+f))
                    end=if(total==0f) stops[index].last() else lerp(end,stops[index].last(),f/(total+f))
                    left+=lefts[index]*f;width+=widths[index]*f;total+=f
                }
            }
            if(total>0f) {
                left/=total;width/=total
                drawRoundRect(Brush.linearGradient(listOf(start,end),Offset(left,0f),Offset(left+width,size.height)),
                    Offset(left,0f),Size(width,size.height),CornerRadius(size.height/2))
            }
        }) {measurables,constraints ->
        val height=54.dp.roundToPx()
        val total=fractions.sumOf {it.value.coerceIn(0f,1f).toDouble()}.toFloat().coerceAtLeast(0.0001f)
        val slot=constraints.maxWidth/(entries.size+DockGrow)
        var x=0f
        val placeables=measurables.mapIndexed {index,measurable ->
            val width=slot*(1f+DockGrow*fractions[index].value.coerceIn(0f,1f)/total)
            lefts[index]=x;widths[index]=width
            val start=x.roundToInt();x+=width
            measurable.measure(Constraints.fixed(x.roundToInt()-start,height)) to start
        }
        layout(constraints.maxWidth,height) {placeables.forEach {(placeable,start) -> placeable.place(start,0)}}
    }
}
/** How much wider than an idle slot the selected tab is. */
private const val DockGrow=1.3f
@Composable private fun DockItem(feature: Feature, selected: Boolean, fraction: State<Float>, onClick: ()->Unit) {
    val description=stringResource(feature.label())
    Layout(content={
        // Two stacked icons cross-fade instead of animating a colour through recomposition.
        Box {
            FeatureIcon(feature,Modifier.size(24.dp).graphicsLayer{alpha=1f-fraction.value.coerceIn(0f,1f)},TvColors.Muted)
            FeatureIcon(feature,Modifier.size(24.dp).graphicsLayer{alpha=fraction.value.coerceIn(0f,1f)},TvColors.OnAccent)
        }
        Text(stringResource(feature.navLabel()),Modifier.graphicsLayer{val f=fraction.value.coerceIn(0f,1f);alpha=f*f},
            style=MaterialTheme.typography.labelLarge,color=TvColors.OnAccent,maxLines=1,softWrap=false,overflow=TextOverflow.Clip)
    },modifier=Modifier.clip(RoundedCornerShape(27.dp)).selectable(selected=selected,role=Role.Tab,onClick=onClick).semantics{contentDescription=description}) {measurables,constraints ->
        val gap=7.dp.roundToPx();val inset=10.dp.roundToPx()
        val icon=measurables[0].measure(Constraints())
        val expanded=constraints.maxWidth.coerceAtLeast(icon.width)
        val label=measurables[1].measure(Constraints(maxWidth=(Constraints.Infinity)))
        layout(constraints.maxWidth,constraints.maxHeight) {
            val f=fraction.value.coerceIn(0f,1f)
            // Alone the icon sits in the middle; selected, icon and label share the middle as a group.
            val alone=(expanded-icon.width)/2f
            val group=((expanded-(icon.width+gap+label.width))/2f).coerceAtLeast(inset.toFloat())
            val iconX=alone+(group-alone)*f
            icon.place(iconX.roundToInt(),(constraints.maxHeight-icon.height)/2)
            label.place((iconX+icon.width+gap).roundToInt(),(constraints.maxHeight-label.height)/2)
        }
    }
}
@Composable internal fun ErrorNotice(error: UiError) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TvColors.Error.copy(alpha=0.09f))
        .border(1.dp,TvColors.Error.copy(alpha=0.2f),RoundedCornerShape(18.dp)).padding(14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        GlyphIcon(Glyph.WIFI,color=TvColors.Error)
        Text(stringResource(error.label()),style=MaterialTheme.typography.bodyMedium,color=TvColors.Error)
    }
}
@Composable internal fun ConnectionCard(state: TvUiState, onConnect: ()->Unit, onDisconnect: ()->Unit) {
    // The glow and the live dot only appear after a real handshake.
    val border by animateColorAsState(if(state.connected) TvColors.Green.copy(alpha=0.6f) else TvColors.Outline,tween(TvMotion.Card),label="connectionBorder")
    val halo by animateFloatAsState(if(state.connected) 1f else 0f,tween(TvMotion.Modal),label="connectionHalo")
    val glow=TvColors.Green
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(TvColors.SurfaceGradient)
        .drawBehind{if(halo>0f){val center=androidx.compose.ui.geometry.Offset(size.height/2,size.height/2);drawCircle(TvColors.glow(glow,center,size.width*0.6f,halo),size.width*0.6f,center)}}
        .border(1.dp,border,RoundedCornerShape(24.dp)).padding(16.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        IconBubble(Feature.MIRROR,size=44.dp)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            val device=state.device?.takeIf{state.connected}
            Text(device?.name ?: state.brand.title,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                StatusDot(state.connected)
                Text(if(device!=null) listOf(stringResource(Res.string.connected),device.model).filter{it.isNotBlank()}.joinToString(" · ") else stringResource(Res.string.not_connected),
                    style=MaterialTheme.typography.bodySmall,color=TvColors.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
        RoundAction(stringResource(if(state.connected) Res.string.disconnect else Res.string.connect_tv),if(state.connected) Glyph.CLOSE else Glyph.RIGHT,
            enabled=!state.busy,accent=TvColors.Cyan,onClick=if(state.connected) onDisconnect else onConnect)
    }
}
/** Green and softly pulsing once connected, a still coral dot otherwise. */
@Composable internal fun StatusDot(live: Boolean) {
    val pulse=rememberPulse(1400,live)
    val color=if(live) TvColors.Green else TvColors.Coral
    Box(Modifier.size(7.dp).drawBehind{if(live) drawCircle(color.copy(alpha=0.35f*(1f-pulse.value)),size.minDimension*(0.5f+0.9f*pulse.value))}.background(color,CircleShape))
}
