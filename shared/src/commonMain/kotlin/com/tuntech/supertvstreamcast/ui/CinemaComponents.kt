package com.tuntech.supertvstreamcast.ui

import androidx.compose.animation.animateColorAsState
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
import supertvstreamcast.shared.generated.resources.*

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
    UiError.PLAYLIST -> Res.string.playlist_error; UiError.INVALID_IP -> Res.string.invalid_ip
    UiError.PERMISSION -> Res.string.lan_denied; UiError.PLAYER -> Res.string.player_error
    UiError.MIRROR -> Res.string.mirror_unavailable; UiError.NO_WIFI -> Res.string.no_wifi
    UiError.APPS -> Res.string.apps_error; UiError.XTREAM_INPUT -> Res.string.xtream_input_error
    UiError.XTREAM -> Res.string.xtream_error; UiError.XTREAM_AUTH -> Res.string.xtream_auth_error
    UiError.FILE -> Res.string.file_error; UiError.STREAM_URL -> Res.string.stream_error
    UiError.GUIDE -> Res.string.guide_error; UiError.GUIDE_COMPRESSED -> Res.string.guide_compressed_error
    UiError.GUIDE_NO_CHANNELS -> Res.string.source_guide_locked
}
@Composable internal fun PrimaryCta(text: String, enabled: Boolean=true, glyph: Glyph?=Glyph.RIGHT, onClick: ()->Unit) {
    Button(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(18.dp),
        contentPadding=PaddingValues(0.dp),colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,disabledContainerColor=TvColors.Raised),
        modifier=Modifier.fillMaxWidth().heightIn(min=TvDimens.Touch)) {
        Row(Modifier.fillMaxWidth().background(if(enabled) TvColors.Gradient else Brush.horizontalGradient(listOf(TvColors.Raised,TvColors.Raised)))
            .padding(horizontal=20.dp,vertical=17.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(text,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,color=if(enabled) TvColors.Background else TvColors.Muted)
            if(glyph!=null) GlyphIcon(glyph,color=if(enabled) TvColors.Background else TvColors.Muted)
        }
    }
}
@Composable internal fun CinemaPanel(modifier: Modifier=Modifier, content: @Composable ColumnScope.()->Unit) {
    Column(modifier.clip(RoundedCornerShape(TvDimens.Radius)).background(TvColors.SurfaceGradient)
        .border(1.dp,TvColors.Outline.copy(alpha=0.7f),RoundedCornerShape(TvDimens.Radius)).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
}
@Composable internal fun IconBubble(feature: Feature, accent: Color=TvColors.Cyan, size: androidx.compose.ui.unit.Dp=48.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha=0.12f)).border(1.dp,accent.copy(alpha=0.15f),RoundedCornerShape(16.dp)),contentAlignment=Alignment.Center) {
        FeatureIcon(feature,Modifier.size(25.dp),accent)
    }
}
@Composable internal fun RoundAction(label: String, glyph: Glyph, modifier: Modifier=Modifier, enabled: Boolean=true, accent: Color=TvColors.Text, onClick: ()->Unit) {
    IconButton(onClick=onClick,enabled=enabled,modifier=modifier.size(52.dp).clip(CircleShape)
        .background(TvColors.Raised).border(1.dp,TvColors.Outline,CircleShape).semantics{contentDescription=label}) {
        GlyphIcon(glyph,color=if(enabled) accent else TvColors.Muted.copy(alpha=0.45f))
    }
}
@Composable internal fun Heading(title: String, body: String?=null) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,style=MaterialTheme.typography.headlineLarge,color=TvColors.Text)
        if(body!=null) Text(body,style=MaterialTheme.typography.bodyLarge,color=TvColors.Muted)
    }
}
@Composable internal fun Artwork(modifier: Modifier=Modifier, resource: DrawableResource=Res.drawable.art_cinema) {
    Image(painterResource(resource),null,modifier.fillMaxWidth().clip(RoundedCornerShape(TvDimens.Radius)),contentScale=ContentScale.Crop)
}
@Composable internal fun Scene(title: String, body: String, badge: String, modifier: Modifier=Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min=270.dp).clip(RoundedCornerShape(30.dp)).border(1.dp,TvColors.Cyan.copy(alpha=0.2f),RoundedCornerShape(30.dp))) {
        Image(painterResource(Res.drawable.art_cinema),null,Modifier.matchParentSize(),contentScale=ContentScale.Crop)
        Box(Modifier.matchParentSize().background(TvColors.HeroScrim))
        Column(Modifier.fillMaxWidth().padding(22.dp),verticalArrangement=Arrangement.spacedBy(70.dp)) {
            Text(badge,Modifier.clip(RoundedCornerShape(100.dp)).background(TvColors.Background.copy(alpha=0.65f))
                .border(1.dp,TvColors.Text.copy(alpha=0.12f),RoundedCornerShape(100.dp)).padding(horizontal=12.dp,vertical=7.dp),
                style=MaterialTheme.typography.labelSmall,color=TvColors.Cyan)
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(title,style=MaterialTheme.typography.displaySmall,color=TvColors.Text)
                Text(body,style=MaterialTheme.typography.bodyMedium,color=TvColors.Muted)
            }
        }
    }
}
@Composable internal fun AppHeader(brand: TvBrand, onSettings: ()->Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        IconBubble(Feature.MIRROR,size=42.dp)
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
@Composable internal fun FloatingDock(tab: Feature, onTab: (Feature)->Unit) {
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=16.dp,vertical=10.dp)
        .clip(RoundedCornerShape(26.dp)).background(TvColors.Surface).border(1.dp,TvColors.Outline,RoundedCornerShape(26.dp)).padding(6.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        Feature.entries.forEach { feature ->
            val selected=tab==feature
            val accent by animateColorAsState(if(selected) TvColors.Cyan else TvColors.Muted,label="dockColor")
            val scale by animateFloatAsState(if(selected) 1f else 0.92f,label="dockScale")
            val description=stringResource(feature.label())
            Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if(selected) TvColors.Cyan.copy(alpha=0.1f) else Color.Transparent)
                .selectable(selected=selected,role=Role.Tab,onClick={onTab(feature)}).semantics{contentDescription=description}
                .padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                FeatureIcon(feature,Modifier.size(23.dp).graphicsLayer{scaleX=scale;scaleY=scale},accent)
                Text(stringResource(feature.navLabel()),style=MaterialTheme.typography.labelMedium,color=accent,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(TvColors.SurfaceGradient)
        .border(1.dp,if(state.connected) TvColors.Cyan.copy(alpha=0.4f) else TvColors.Outline,RoundedCornerShape(24.dp)).padding(16.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        IconBubble(Feature.MIRROR,size=44.dp)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            val device=state.device?.takeIf{state.connected}
            Text(device?.name ?: state.brand.title,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(5.dp).background(if(state.connected) TvColors.Cyan else TvColors.Coral,CircleShape))
                Text(if(device!=null) listOf(stringResource(Res.string.connected),device.model).filter{it.isNotBlank()}.joinToString(" · ") else stringResource(Res.string.not_connected),
                    style=MaterialTheme.typography.bodySmall,color=TvColors.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
        RoundAction(stringResource(if(state.connected) Res.string.disconnect else Res.string.connect_tv),if(state.connected) Glyph.CLOSE else Glyph.RIGHT,
            enabled=!state.busy,accent=TvColors.Cyan,onClick=if(state.connected) onDisconnect else onConnect)
    }
}
