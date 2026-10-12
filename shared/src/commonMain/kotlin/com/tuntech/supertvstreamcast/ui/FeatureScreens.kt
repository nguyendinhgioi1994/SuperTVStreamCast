package com.tuntech.supertvstreamcast.ui

import com.tuntech.monetization.iap.IapActiveBox
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.theme.*
import org.jetbrains.compose.resources.*
import shared.resources.*

@Composable internal fun HomeContent(state: TvUiState,onConnect: ()->Unit,onDisconnect: ()->Unit,onPremium: (String)->Unit,onTab: (Feature)->Unit) {
    // Free users get the premium slides in the hero spot; premium users keep the artwork scene.
    Reveal(0) {
        IapActiveBox(allowAll=true) {active ->
            if(active) Scene(stringResource(Res.string.home_title),stringResource(Res.string.home_body),stringResource(Res.string.hero_badge))
            else PremiumCarousel(onPremium)
        }
    }
    Reveal(1) {ConnectionCard(state,onConnect,onDisconnect)}
    Reveal(2) {Text(stringResource(Res.string.quick_access),style=MaterialTheme.typography.titleLarge)}
    Reveal(3) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            FeatureCard(Feature.REMOTE,stringResource(Res.string.remote_body),Modifier.weight(1f).fillMaxHeight()){onTab(Feature.REMOTE)}
            FeatureCard(Feature.MIRROR,stringResource(Res.string.mirror_body),Modifier.weight(1f).fillMaxHeight()){onTab(Feature.MIRROR)}
        }
    }
    Reveal(4) {
        GradientCard(TvColors.featureGradient(Feature.IPTV),Modifier.fillMaxWidth(),onClick={onTab(Feature.IPTV)}) {
            Row(Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Image(painterResource(TvArt.iptv),null,Modifier.size(width=96.dp,height=72.dp).clip(RoundedCornerShape(18.dp))
                    .border(1.5.dp,TvColors.OnAccent.copy(alpha=0.5f),RoundedCornerShape(18.dp)),contentScale=ContentScale.Crop)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.iptv),style=MaterialTheme.typography.titleLarge,color=TvColors.OnAccent)
                    Text(stringResource(Res.string.iptv_body),style=MaterialTheme.typography.bodyMedium,color=TvColors.OnAccent.copy(alpha=0.88f))
                }
                ArrowChip()
            }
        }
    }
}
/** A tile filled with its feature's gradient, with the duotone icon and white copy on top. */
@Composable private fun FeatureCard(feature: Feature,body: String,modifier: Modifier,onClick: ()->Unit) {
    GradientCard(TvColors.featureGradient(feature),modifier,onClick=onClick) {
        Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(TvColors.OnAccent.copy(alpha=0.2f)),contentAlignment=Alignment.Center) {
                    FeatureIcon(feature,Modifier.size(28.dp),TvColors.OnAccent)
                }
                ArrowChip()
            }
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(stringResource(feature.label()),style=MaterialTheme.typography.titleMedium,color=TvColors.OnAccent)
                Text(body,style=MaterialTheme.typography.bodyMedium,color=TvColors.OnAccent.copy(alpha=0.88f))
            }
        }
    }
}
/** Two soft highlights that make a gradient surface look glossy. */
internal fun Modifier.gloss(): Modifier = drawBehind {
    drawCircle(Color.White.copy(alpha=0.16f),size.width*0.45f,Offset(size.width*0.95f,-size.height*0.1f))
    drawCircle(Color.White.copy(alpha=0.08f),size.width*0.3f,Offset(size.width*0.05f,size.height*1.1f))
}
@Composable internal fun BrandGrid(selected: TvBrand,onBrand: (TvBrand)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        TvBrand.entries.chunked(2).forEach {row ->
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                row.forEach {brand ->
                    val active=selected==brand
                    val border by animateColorAsState(if(active) TvColors.Cyan.copy(alpha=0.7f) else TvColors.Outline,label="brandBorder")
                    val fill by animateColorAsState(if(active) TvColors.Cyan.copy(alpha=0.1f) else TvColors.Surface,label="brandFill")
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(TvColors.Surface).background(fill)
                        .border(1.dp,border,RoundedCornerShape(20.dp)).selectable(selected=active,role=Role.RadioButton,onClick={onBrand(brand)})
                        .padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(TvColors.Raised).then(if(active) Modifier.background(TvColors.Gradient) else Modifier),contentAlignment=Alignment.Center) {
                                Text(if(brand==TvBrand.LG) "LG" else brand.title.take(1),style=MaterialTheme.typography.titleSmall,color=if(active) TvColors.OnAccent else TvColors.Muted)
                            }
                            if(active) Box(Modifier.size(22.dp).background(TvColors.Gradient,CircleShape),contentAlignment=Alignment.Center) {GlyphIcon(Glyph.CHECK,Modifier.size(13.dp),TvColors.OnAccent)}
                        }
                        Text(brand.title,style=MaterialTheme.typography.labelLarge,color=if(active) TvColors.Text else TvColors.Muted)
                    }
                }
            }
        }
    }
}
/** The Cast tab (Feature.MIRROR): media casting to DLNA / Chromecast plus the system screen-mirroring guide. */
@Composable internal fun MirrorContent(onShare: (() -> Unit)?,onIptv: ()->Unit={}) {
    com.tuntech.supertvstreamcast.ui.cast.CastTab(onShare,onIptv)
}
@Composable internal fun SettingsContent(state: TvUiState,onTvSettings: ()->Unit,onPremium: ()->Unit={},onTheme: (TvThemeMode)->Unit={},
    links: SettingsLinks=SettingsLinks()) {
    Heading(stringResource(Res.string.settings_title),stringResource(Res.string.tagline))
    IapActiveBox {
        val premiumShape=RoundedCornerShape(24.dp)
        Card(onClick=onPremium,shape=premiumShape,colors=CardDefaults.cardColors(containerColor=Color.Transparent),
            modifier=Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().background(TvColors.PremiumGradient).gloss().shimmer().padding(18.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(16.dp)).background(TvColors.OnAccent.copy(alpha=0.22f)),contentAlignment=Alignment.Center) {GlyphIcon(Glyph.CROWN,color=TvColors.OnAccent)}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.premium_title),style=MaterialTheme.typography.titleLarge,color=TvColors.OnAccent)
                    Text(stringResource(Res.string.premium_body),style=MaterialTheme.typography.bodyMedium,color=TvColors.OnAccent.copy(alpha=0.88f))
                }
                ArrowChip()
            }
        }
    }
    TvSettingsRow(state,onTvSettings)
    Text(stringResource(Res.string.appearance_title),style=MaterialTheme.typography.titleMedium)
    ThemeSelector(LocalTvThemeMode.current,onTheme)
    SettingsGroups(links)
    Text(stringResource(Res.string.about),Modifier.fillMaxWidth(),style=MaterialTheme.typography.labelLarge,color=TvColors.Coral,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
}
/** One compact row for the TV: brand and connection, opening [TvSettingsContent]. */
@Composable private fun TvSettingsRow(state: TvUiState,onClick: ()->Unit) {
    val shape=RoundedCornerShape(TvDimens.Radius)
    val status=state.device?.name?.takeIf{state.connected&&it.isNotBlank()} ?: stringResource(if(state.connected) Res.string.connected else Res.string.not_connected)
    Row(Modifier.fillMaxWidth().clip(shape).background(TvColors.SurfaceGradient).border(1.dp,TvColors.Outline.copy(alpha=0.7f),shape)
        .clickable(role=Role.Button,onClick=onClick).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(TvColors.Gradient),contentAlignment=Alignment.Center) {
            Text(if(state.brand==TvBrand.LG) "LG" else state.brand.title.take(1),style=MaterialTheme.typography.titleMedium,color=TvColors.OnAccent)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(stringResource(Res.string.tv_settings_title),style=MaterialTheme.typography.titleMedium)
            Text("${state.brand.title} · $status",style=MaterialTheme.typography.bodyMedium,color=if(state.connected) TvColors.Cyan else TvColors.Muted,
                maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        GlyphIcon(Glyph.RIGHT,Modifier.size(18.dp),TvColors.Muted)
    }
}
/** Settings → TV: the brand choice plus what is stored about paired TVs and how to forget it. */
@Composable internal fun TvSettingsContent(state: TvUiState,onBack: ()->Unit,onBrand: (TvBrand)->Unit,onForgetTvs: ()->Unit) {
    com.tuntech.supertvstreamcast.ui.iptv.TopRow(stringResource(Res.string.tv_settings_title),onBack=onBack)
    Text(stringResource(Res.string.choose_brand),style=MaterialTheme.typography.titleMedium)
    BrandGrid(state.brand,onBrand)
    CinemaPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            GlyphIcon(Glyph.LOCK,color=TvColors.Cyan);Text(stringResource(Res.string.privacy_title),style=MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(Res.string.privacy_body),style=MaterialTheme.typography.bodyMedium,color=TvColors.Muted)
        OutlinedButton(onClick=onForgetTvs,enabled=!state.busy,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text(stringResource(Res.string.forget_tvs))
        }
    }
}
/** System / Light / Dark. The thumb slides to the active choice. */
@Composable internal fun ThemeSelector(mode: TvThemeMode,onMode: (TvThemeMode)->Unit) {
    val shape=RoundedCornerShape(TvDimens.RadiusMd)
    val thumb=RoundedCornerShape(14.dp)
    val options=listOf(Triple(TvThemeMode.SYSTEM,Glyph.AUTO,Res.string.theme_system),Triple(TvThemeMode.LIGHT,Glyph.SUN,Res.string.theme_light),
        Triple(TvThemeMode.DARK,Glyph.MOON,Res.string.theme_dark))
    BoxWithConstraints(Modifier.fillMaxWidth().clip(shape).background(TvColors.Raised).border(1.dp,TvColors.Outline,shape).padding(4.dp)) {
        val slot=maxWidth/options.size
        val x by animateDpAsState(slot*options.indexOfFirst{it.first==mode},if(LocalReducedMotion.current) snap() else TvMotion.selection(),label="themeThumb")
        Box(Modifier.matchParentSize()) {
            Box(Modifier.offset{IntOffset(x.roundToPx(),0)}.width(slot).fillMaxHeight().shadow(6.dp,thumb,ambientColor=TvColors.Pink,spotColor=TvColors.Pink).clip(thumb).background(TvColors.Gradient))
        }
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            options.forEach {(option,glyph,label) ->
                val active=option==mode
                val color by animateColorAsState(if(active) TvColors.OnAccent else TvColors.Muted,label="themeOption")
                Row(Modifier.weight(1f).heightIn(min=48.dp).clip(thumb).selectable(selected=active,role=Role.RadioButton,onClick={onMode(option)}).padding(horizontal=6.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp,Alignment.CenterHorizontally)) {
                    GlyphIcon(glyph,Modifier.size(18.dp),color)
                    Text(stringResource(label),style=MaterialTheme.typography.labelLarge,color=color,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
}
