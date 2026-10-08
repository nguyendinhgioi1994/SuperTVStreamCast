package com.tuntech.supertvstreamcast.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.theme.*
import org.jetbrains.compose.resources.*
import supertvstreamcast.shared.generated.resources.*

@Composable internal fun HomeContent(state: TvUiState,onConnect: ()->Unit,onDisconnect: ()->Unit,onTab: (Feature)->Unit) {
    Scene(stringResource(Res.string.home_title),stringResource(Res.string.home_body),stringResource(Res.string.hero_badge))
    ConnectionCard(state,onConnect,onDisconnect)
    Text(stringResource(Res.string.quick_access),style=MaterialTheme.typography.titleLarge)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        FeatureCard(Feature.REMOTE,stringResource(Res.string.remote_body),TvColors.Cyan,Modifier.weight(1f)){onTab(Feature.REMOTE)}
        FeatureCard(Feature.MIRROR,stringResource(Res.string.mirror_body),TvColors.Violet,Modifier.weight(1f)){onTab(Feature.MIRROR)}
    }
    Card(onClick={onTab(Feature.IPTV)},shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=TvColors.Surface),
        border=BorderStroke(1.dp,TvColors.Coral.copy(alpha=0.18f)),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            IconBubble(Feature.IPTV,TvColors.Coral)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.iptv),style=MaterialTheme.typography.titleLarge)
                Text(stringResource(Res.string.iptv_body),style=MaterialTheme.typography.bodyMedium,color=TvColors.Muted)
            }
            GlyphIcon(Glyph.RIGHT,color=TvColors.Coral)
        }
    }
}
@Composable private fun FeatureCard(feature: Feature,body: String,accent: Color,modifier: Modifier,onClick: ()->Unit) {
    Card(onClick=onClick,modifier=modifier,shape=RoundedCornerShape(26.dp),border=BorderStroke(1.dp,accent.copy(alpha=0.18f)),colors=CardDefaults.cardColors(containerColor=TvColors.Surface)) {
        Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(accent.copy(alpha=0.1f),Color.Transparent)))
            .padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                IconBubble(feature,accent,size=44.dp);GlyphIcon(Glyph.RIGHT,Modifier.size(18.dp),accent)
            }
            Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(stringResource(feature.label()),style=MaterialTheme.typography.titleMedium)
                Text(body,style=MaterialTheme.typography.bodyMedium,color=TvColors.Muted)
            }
        }
    }
}
@Composable internal fun BrandGrid(selected: TvBrand,onBrand: (TvBrand)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        TvBrand.entries.chunked(2).forEach {row ->
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                row.forEach {brand ->
                    val active=selected==brand
                    val border by animateColorAsState(if(active) TvColors.Cyan.copy(alpha=0.7f) else TvColors.Outline,label="brandBorder")
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if(active) TvColors.Cyan.copy(alpha=0.08f) else TvColors.Surface)
                        .border(1.dp,border,RoundedCornerShape(20.dp)).selectable(selected=active,role=Role.RadioButton,onClick={onBrand(brand)})
                        .padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(TvColors.Raised),contentAlignment=Alignment.Center) {
                                Text(if(brand==TvBrand.LG) "LG" else brand.title.take(1),style=MaterialTheme.typography.titleSmall,color=if(active) TvColors.Cyan else TvColors.Muted)
                            }
                            if(active) GlyphIcon(Glyph.CHECK,Modifier.size(18.dp),TvColors.Cyan)
                        }
                        Text(brand.title,style=MaterialTheme.typography.labelLarge,color=if(active) TvColors.Text else TvColors.Muted)
                    }
                }
            }
        }
    }
}
@Composable internal fun OnboardingScaffold(state: TvUiState,onNext: ()->Unit,onBack: ()->Unit,onBrand: (TvBrand)->Unit,onGoal: (Feature)->Unit) {
    Column(Modifier.fillMaxSize().padding(TvDimens.Space),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Text(stringResource(Res.string.app_name),style=MaterialTheme.typography.titleLarge,color=TvColors.Cyan)
            Text(stringResource(Res.string.onboarding_step,state.step+1),style=MaterialTheme.typography.labelSmall,color=TvColors.Muted)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            repeat(3){step -> Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(if(step<=state.step) TvColors.Cyan else TvColors.Outline))}
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(22.dp)) {
            when(state.step) {
                0 -> {
                    Artwork(Modifier.height(280.dp))
                    Heading(stringResource(Res.string.welcome_title),stringResource(Res.string.welcome_body))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                        listOf(Feature.REMOTE,Feature.MIRROR,Feature.IPTV).forEach {feature ->
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                IconBubble(feature,size=44.dp);Text(stringResource(feature.navLabel()),style=MaterialTheme.typography.labelMedium,color=TvColors.Muted)
                            }
                        }
                    }
                }
                1 -> {Heading(stringResource(Res.string.brand_title),stringResource(Res.string.brand_body));BrandGrid(state.brand,onBrand)}
                else -> {
                    Artwork(Modifier.height(170.dp))
                    Heading(stringResource(Res.string.goal_title),stringResource(Res.string.goal_body))
                    listOf(Feature.REMOTE,Feature.MIRROR,Feature.IPTV).forEach {goal ->
                        val active=state.goal==goal
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(if(active) TvColors.Raised else TvColors.Surface)
                            .border(1.dp,if(active) TvColors.Cyan.copy(alpha=0.6f) else TvColors.Outline,RoundedCornerShape(22.dp))
                            .selectable(selected=active,role=Role.RadioButton,onClick={onGoal(goal)}).padding(16.dp),
                            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            IconBubble(goal,size=42.dp)
                            Text(stringResource(goal.label()),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                            RadioButton(selected=active,onClick=null)
                        }
                    }
                }
            }
        }
        PrimaryCta(stringResource(if(state.step==2) Res.string.get_started else Res.string.continue_label),onClick=onNext)
        if(state.step>0) TextButton(onClick=onBack,modifier=Modifier.align(Alignment.CenterHorizontally)){Text(stringResource(Res.string.back),color=TvColors.Muted)}
    }
}
@Composable internal fun RemoteContent(state: TvUiState,onConnect: ()->Unit,onDisconnect: ()->Unit,onKey: (RemoteKey)->Unit) {
    Heading(stringResource(Res.string.remote),stringResource(Res.string.remote_caption))
    ConnectionCard(state,onConnect,onDisconnect)
    val enabled=state.connected&&!state.busy
    CinemaPanel(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
            Text(state.brand.title,style=MaterialTheme.typography.labelLarge,color=TvColors.Muted)
            RoundAction(stringResource(Res.string.power),Glyph.POWER,enabled=enabled,accent=TvColors.Coral){onKey(RemoteKey.POWER)}
        }
        BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center) {
            val size=minOf(maxWidth,248.dp)
            Box(Modifier.size(size).clip(CircleShape).background(TvColors.RemoteGradient).border(1.dp,TvColors.Outline,CircleShape)) {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(TvColors.Cyan.copy(alpha=0.08f),radius=this.size.minDimension/2-12.dp.toPx(),style=Stroke(1.dp.toPx()))
                    drawCircle(TvColors.Background.copy(alpha=0.35f),radius=this.size.minDimension/2-62.dp.toPx(),style=Stroke(1.dp.toPx()))
                }
                DirectionKey(RemoteKey.UP,Glyph.UP,stringResource(Res.string.up),enabled,Modifier.align(Alignment.TopCenter).padding(top=10.dp),onKey)
                DirectionKey(RemoteKey.DOWN,Glyph.DOWN,stringResource(Res.string.down),enabled,Modifier.align(Alignment.BottomCenter).padding(bottom=10.dp),onKey)
                DirectionKey(RemoteKey.LEFT,Glyph.LEFT,stringResource(Res.string.left),enabled,Modifier.align(Alignment.CenterStart).padding(start=10.dp),onKey)
                DirectionKey(RemoteKey.RIGHT,Glyph.RIGHT,stringResource(Res.string.right),enabled,Modifier.align(Alignment.CenterEnd).padding(end=10.dp),onKey)
                Button(onClick={onKey(RemoteKey.OK)},enabled=enabled,contentPadding=PaddingValues(0.dp),shape=CircleShape,
                    colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,disabledContainerColor=TvColors.Raised),modifier=Modifier.align(Alignment.Center).size(78.dp)) {
                    Box(Modifier.fillMaxSize().background(if(enabled) TvColors.Gradient else TvColors.SurfaceGradient),contentAlignment=Alignment.Center) {
                        Text(stringResource(Res.string.ok),style=MaterialTheme.typography.titleLarge,color=if(enabled) TvColors.Background else TvColors.Muted)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
            RoundAction(stringResource(Res.string.back),Glyph.BACK,enabled=enabled){onKey(RemoteKey.BACK)}
            val homeLabel=stringResource(Res.string.tv_home)
            IconButton(onClick={onKey(RemoteKey.HOME)},enabled=enabled,modifier=Modifier.size(52.dp).clip(CircleShape).background(TvColors.Raised).semantics{contentDescription=homeLabel}) {
                FeatureIcon(Feature.HOME,Modifier.size(24.dp),if(enabled) TvColors.Text else TvColors.Muted.copy(alpha=0.45f))
            }
            RoundAction(stringResource(Res.string.mute),Glyph.MUTE,enabled=enabled){onKey(RemoteKey.MUTE)}
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TvColors.Background.copy(alpha=0.6f)).padding(8.dp),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            RoundAction(stringResource(Res.string.volume_down),Glyph.MINUS,enabled=enabled){onKey(RemoteKey.VOLUME_DOWN)}
            Text(stringResource(Res.string.volume),Modifier.weight(1f),style=MaterialTheme.typography.titleSmall,color=TvColors.Muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            RoundAction(stringResource(Res.string.volume_up),Glyph.PLUS,enabled=enabled){onKey(RemoteKey.VOLUME_UP)}
        }
    }
    if(!state.connected) Text(stringResource(Res.string.connect_first),Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodySmall,color=TvColors.Muted,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
}
@Composable private fun DirectionKey(key: RemoteKey,glyph: Glyph,label: String,enabled: Boolean,modifier: Modifier,onKey: (RemoteKey)->Unit) {
    IconButton(onClick={onKey(key)},enabled=enabled,modifier=modifier.size(58.dp).semantics{contentDescription=label}) {
        GlyphIcon(glyph,Modifier.size(28.dp),if(enabled) TvColors.Text else TvColors.Muted.copy(alpha=0.45f))
    }
}
@Composable internal fun MirrorContent(onShare: (() -> Unit)?) {
    Heading(stringResource(Res.string.mirror_title))
    Artwork(Modifier.height(200.dp),Res.drawable.art_mirror)
    CinemaPanel(Modifier.fillMaxWidth()) {
        val steps=listOf(Res.string.mirror_step_wifi to Res.string.mirror_step_wifi_body,
            Res.string.mirror_step_share to Res.string.mirror_step_share_body,Res.string.mirror_step_tv to Res.string.mirror_step_tv_body)
        steps.forEachIndexed {index,(title,body) ->
            Row(Modifier.padding(vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(32.dp).background(TvColors.Violet.copy(alpha=0.14f),CircleShape),contentAlignment=Alignment.Center) {
                    Text("${index+1}",style=MaterialTheme.typography.labelLarge,color=TvColors.Violet)
                }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(title),style=MaterialTheme.typography.titleSmall)
                    Text(stringResource(body),style=MaterialTheme.typography.bodyMedium,color=TvColors.Muted)
                }
            }
        }
    }
    if(onShare!=null) PrimaryCta(stringResource(Res.string.mirror_action),onClick=onShare)
    else CinemaPanel {Text(stringResource(Res.string.mirror_ios),style=MaterialTheme.typography.bodyLarge,color=TvColors.Cyan)}
    Text(stringResource(Res.string.mirror_note),style=MaterialTheme.typography.bodySmall,color=TvColors.Muted)
}
@Composable internal fun SettingsContent(state: TvUiState,onBrand: (TvBrand)->Unit) {
    Heading(stringResource(Res.string.settings_title),stringResource(Res.string.tagline))
    Text(stringResource(Res.string.choose_brand),style=MaterialTheme.typography.titleMedium)
    BrandGrid(state.brand,onBrand)
    CinemaPanel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            GlyphIcon(Glyph.LOCK,color=TvColors.Cyan);Text(stringResource(Res.string.privacy_title),style=MaterialTheme.typography.titleMedium)
        }
        Text(stringResource(Res.string.privacy_body),style=MaterialTheme.typography.bodyMedium,color=TvColors.Muted)
    }
    Text(stringResource(Res.string.about),Modifier.fillMaxWidth(),style=MaterialTheme.typography.labelLarge,color=TvColors.Coral,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
}
