package com.tuntech.supertvstreamcast.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.platform.StreamPlayer
import com.tuntech.supertvstreamcast.theme.*
import org.jetbrains.compose.resources.*
import supertvstreamcast.shared.generated.resources.*

@Composable fun TvScaffold(state: TvUiState, actions: TvActions) {
    Scaffold(containerColor=TvColors.Background,bottomBar={
        if(state.step==3 && state.player==null) FloatingDock(state.tab,actions.tab)
    }) { padding ->
        Box(Modifier.fillMaxSize().background(TvColors.BackgroundGradient).padding(padding)) {
            when {
                state.step<3 -> OnboardingScaffold(state,actions.next,actions.back,actions.brand,actions.goal)
                state.player!=null -> PlayerContent(state,state.player,actions)
                else -> AnimatedContent(state.tab,transitionSpec={fadeIn() togetherWith fadeOut()},label="tabTransition") { tab ->
                    if(tab==Feature.IPTV) PlaylistScaffold(state,actions)
                    else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=TvDimens.Space)
                        .padding(top=8.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                        AppHeader(state.brand){actions.tab(Feature.SETTINGS)}
                        state.error?.let {ErrorNotice(it)}
                        when(tab) {
                            Feature.HOME -> HomeContent(state,{actions.connection(true)},actions.disconnect,actions.tab)
                            Feature.REMOTE -> RemoteContent(state,actions)
                            Feature.MIRROR -> MirrorContent(actions.share)
                            Feature.SETTINGS -> SettingsContent(state,actions.brand)
                            Feature.IPTV -> Unit
                        }
                    }
                }
            }
        }
    }
    if(state.connectionOpen) ConnectionDialog(state,actions)
}
@Composable private fun PlayerContent(state: TvUiState,channel: Channel,actions: TvActions) {
    Column(Modifier.fillMaxSize().padding(TvDimens.Space),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        TextButton(onClick={actions.tab(Feature.IPTV)}) {GlyphIcon(Glyph.BACK);Spacer(Modifier.width(8.dp));Text(stringResource(Res.string.library))}
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(channel.title,style=MaterialTheme.typography.headlineSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
            if(channel.group.isNotBlank()) Text(channel.group,style=MaterialTheme.typography.bodyMedium,color=TvColors.Muted)
            val now=rememberEpochSeconds()
            NowNext(upcoming(state.guide[channel.url],now),now)
        }
        key(channel.url) {StreamPlayer(channel.url,Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)),actions.playerError)}
        state.error?.let { ErrorNotice(it) }
        val zapping=state.channels.size>1&&state.channels.any{it.url==channel.url}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceEvenly) {
            RoundAction(stringResource(Res.string.previous_channel),Glyph.LEFT,enabled=zapping){actions.zap(-1)}
            val favorite=channel.url in state.favorites
            RoundAction(stringResource(Res.string.favorite_action),Glyph.HEART,accent=if(favorite) TvColors.Coral else TvColors.Muted){actions.favorite(channel.url)}
            RoundAction(stringResource(Res.string.next_channel),Glyph.RIGHT,enabled=zapping){actions.zap(1)}
        }
    }
}
private fun TvBrand.setupText() = when(this) {
    TvBrand.SONY -> Res.string.sony_setup; TvBrand.SAMSUNG -> Res.string.samsung_setup
    TvBrand.LG -> Res.string.lg_setup; else -> Res.string.planned_brand
}
@Composable private fun ConnectionDialog(state: TvUiState,actions: TvActions) {
    var ip by remember(state.lastHost) {mutableStateOf(state.lastHost)}
    var psk by remember {mutableStateOf("")}
    val supported=state.brand.hasRemoteAdapter
    AlertDialog(onDismissRequest={actions.connection(false)},shape=RoundedCornerShape(30.dp),containerColor=TvColors.Surface,
        icon={IconBubble(Feature.MIRROR)},title={Text(stringResource(Res.string.connection_title),style=MaterialTheme.typography.headlineSmall)},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(stringResource(state.brand.setupText()),style=MaterialTheme.typography.bodyMedium,color=TvColors.Muted)
            OutlinedButton(onClick=actions.scan,enabled=!state.scanning&&!state.busy,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                GlyphIcon(Glyph.SEARCH,Modifier.size(18.dp),TvColors.Cyan);Spacer(Modifier.width(8.dp));Text(stringResource(Res.string.scan_network))
            }
            if(state.scanning) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(Res.string.scanning),style=MaterialTheme.typography.bodySmall,color=TvColors.Muted)
            }
            if(state.discovered.isNotEmpty()) {
                Text(stringResource(Res.string.found_tvs),style=MaterialTheme.typography.titleSmall)
                state.discovered.forEach {device -> DeviceRow(device,!state.busy){actions.pick(device)}}
            } else if(state.scanned&&!state.scanning) Text(stringResource(Res.string.scan_empty),style=MaterialTheme.typography.bodySmall,color=TvColors.Muted)
            if(supported) {
                Text(stringResource(Res.string.manual_ip),style=MaterialTheme.typography.titleSmall)
                OutlinedTextField(ip,{ip=it.trim()},enabled=!state.busy,label={Text(stringResource(Res.string.ip_address))},singleLine=true,shape=RoundedCornerShape(16.dp))
                if(state.brand.needsPsk) OutlinedTextField(psk,{psk=it},enabled=!state.busy,label={Text(stringResource(Res.string.psk))},singleLine=true,shape=RoundedCornerShape(16.dp),visualTransformation=PasswordVisualTransformation())
            }
            state.error?.let {ErrorNotice(it)}
            if(state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                if(!state.brand.needsPsk) Text(stringResource(Res.string.accept_on_tv),style=MaterialTheme.typography.bodyMedium,color=TvColors.Cyan)
            }
        }},confirmButton={if(supported) TextButton(onClick={actions.connect(ip,psk)},enabled=!state.busy&&ip.isNotBlank()){Text(stringResource(Res.string.connect))}},
        dismissButton={TextButton(onClick={actions.connection(false)},enabled=!state.busy){Text(stringResource(Res.string.back))}})
}
@Composable private fun DeviceRow(device: TvDevice,enabled: Boolean,onClick: ()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TvColors.Raised).border(1.dp,TvColors.Outline,RoundedCornerShape(18.dp))
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick).padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(TvColors.Cyan.copy(alpha=0.12f)),contentAlignment=Alignment.Center) {
            Text(if(device.brand==TvBrand.LG) "LG" else device.brand.title.take(1),style=MaterialTheme.typography.titleSmall,color=TvColors.Cyan)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(device.name,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(listOf(device.model,device.host).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=TvColors.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        GlyphIcon(Glyph.RIGHT,Modifier.size(18.dp),TvColors.Cyan)
    }
}
@Preview(widthDp=360,heightDp=780)
@Preview(widthDp=320,heightDp=640)
@Composable private fun HomePreview() {TvTheme {PreviewScaffold(TvUiState(step=3))}}
@Preview(widthDp=360,heightDp=780)
@Composable private fun OnboardingPreview() {TvTheme {PreviewScaffold(TvUiState())}}
@Preview(widthDp=360,heightDp=780)
@Composable private fun RemotePreview() {TvTheme {PreviewScaffold(TvUiState(step=3,tab=Feature.REMOTE,brand=TvBrand.LG,connected=true,
    device=TvDevice(TvBrand.LG,"192.168.1.7","Preview TV"),capabilities=LgProtocol.capabilities(pointer=true),apps=listOf(TvApp("preview","Preview app"))))}}
@Preview(widthDp=360,heightDp=780)
@Composable private fun ConnectionPreview() {TvTheme {PreviewScaffold(TvUiState(step=3,brand=TvBrand.SAMSUNG,connectionOpen=true,scanned=true,
    discovered=listOf(TvDevice(TvBrand.SAMSUNG,"192.168.1.9","Preview TV","Model"))))}}
@Preview(widthDp=320,heightDp=640)
@Composable private fun SmallRemotePreview() {TvTheme {PreviewScaffold(TvUiState(step=3,tab=Feature.REMOTE))}}
@Preview(widthDp=360,heightDp=780)
@Composable private fun MirrorPreview() {TvTheme {PreviewScaffold(TvUiState(step=3,tab=Feature.MIRROR))}}
@Preview(widthDp=360,heightDp=780)
@Composable private fun EmptyPlaylistPreview() {TvTheme {PreviewScaffold(TvUiState(step=3,tab=Feature.IPTV))}}
@Preview(widthDp=360,heightDp=780)
@Composable private fun PlaylistErrorPreview() {TvTheme {PreviewScaffold(TvUiState(step=3,tab=Feature.IPTV,error=UiError.PLAYLIST))}}
@Preview(widthDp=360,heightDp=780)
@Composable private fun LoadingPreview() {TvTheme {PreviewScaffold(TvUiState(step=3,tab=Feature.IPTV,busy=true))}}
@Preview(widthDp=360,heightDp=780)
@Composable private fun PermissionDeniedPreview() {TvTheme {PreviewScaffold(TvUiState(step=3,tab=Feature.REMOTE,error=UiError.PERMISSION))}}
@Preview(widthDp=360,heightDp=780)
@Composable private fun PopulatedPlaylistPreview() {TvTheme {PreviewScaffold(TvUiState(step=3,tab=Feature.IPTV,channels=listOf(Channel("https://example.com/live","Example channel","News"))))}}
@Composable private fun PreviewScaffold(state: TvUiState) {
    TvScaffold(state,TvActions())
}
