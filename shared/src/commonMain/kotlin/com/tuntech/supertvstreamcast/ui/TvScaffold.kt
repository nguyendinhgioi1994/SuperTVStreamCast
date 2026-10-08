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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.platform.StreamPlayer
import com.tuntech.supertvstreamcast.theme.*
import org.jetbrains.compose.resources.*
import supertvstreamcast.shared.generated.resources.*

@Composable fun TvScaffold(
    state: TvUiState, onNext: ()->Unit, onBack: ()->Unit, onBrand: (TvBrand)->Unit,
    onGoal: (Feature)->Unit, onTab: (Feature)->Unit, onConnection: (Boolean)->Unit,
    onConnect: (String,String)->Unit, onDisconnect: ()->Unit, onKey: (RemoteKey)->Unit,
    onImport: (String)->Unit, onFavorite: (String)->Unit, onPlay: (Channel)->Unit,
    onPlayerError: ()->Unit, onShare: (() -> Unit)?,
) {
    Scaffold(containerColor=TvColors.Background,bottomBar={
        if(state.step==3 && state.player==null) FloatingDock(state.tab,onTab)
    }) { padding ->
        Box(Modifier.fillMaxSize().background(TvColors.BackgroundGradient).padding(padding)) {
            when {
                state.step<3 -> OnboardingScaffold(state,onNext,onBack,onBrand,onGoal)
                state.player!=null -> Column(Modifier.fillMaxSize().padding(TvDimens.Space),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick={onTab(Feature.IPTV)}) {GlyphIcon(Glyph.BACK);Spacer(Modifier.width(8.dp));Text(stringResource(Res.string.library))}
                    Text(state.player.title,style=MaterialTheme.typography.headlineSmall)
                    StreamPlayer(state.player.url,Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(24.dp)),onPlayerError)
                    state.error?.let { ErrorNotice(it) }
                }
                else -> AnimatedContent(state.tab,transitionSpec={fadeIn() togetherWith fadeOut()},label="tabTransition") { tab ->
                    if(tab==Feature.IPTV) PlaylistScaffold(state,onImport,onFavorite,onPlay)
                    else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=TvDimens.Space)
                        .padding(top=8.dp,bottom=16.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                        AppHeader(state.brand){onTab(Feature.SETTINGS)}
                        state.error?.let {ErrorNotice(it)}
                        when(tab) {
                            Feature.HOME -> HomeContent(state,{onConnection(true)},onDisconnect,onTab)
                            Feature.REMOTE -> RemoteContent(state,{onConnection(true)},onDisconnect,onKey)
                            Feature.MIRROR -> MirrorContent(onShare)
                            Feature.SETTINGS -> SettingsContent(state,onBrand)
                            Feature.IPTV -> Unit
                        }
                    }
                }
            }
        }
    }
    if(state.connectionOpen) ConnectionDialog(state,{onConnection(false)},onConnect)
}
@Composable private fun ConnectionDialog(state: TvUiState,onDismiss: ()->Unit,onConnect: (String,String)->Unit) {
    var ip by rememberSaveable {mutableStateOf("")}
    var psk by remember {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,shape=RoundedCornerShape(30.dp),containerColor=TvColors.Surface,
        icon={IconBubble(Feature.MIRROR)},title={Text(stringResource(Res.string.connection_title),style=MaterialTheme.typography.headlineSmall)},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(stringResource(if(state.brand==TvBrand.SONY) Res.string.sony_setup else Res.string.planned_brand),style=MaterialTheme.typography.bodyMedium,color=TvColors.Muted)
            if(state.brand==TvBrand.SONY) {
                OutlinedTextField(ip,{ip=it},enabled=!state.busy,label={Text(stringResource(Res.string.ip_address))},singleLine=true,shape=RoundedCornerShape(16.dp))
                OutlinedTextField(psk,{psk=it},enabled=!state.busy,label={Text(stringResource(Res.string.psk))},singleLine=true,shape=RoundedCornerShape(16.dp),visualTransformation=PasswordVisualTransformation())
            }
            state.error?.let {ErrorNotice(it)}
            if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }},confirmButton={if(state.brand==TvBrand.SONY) TextButton(onClick={onConnect(ip,psk)},enabled=!state.busy){Text(stringResource(Res.string.connect))}},
        dismissButton={TextButton(onClick=onDismiss,enabled=!state.busy){Text(stringResource(Res.string.back))}})
}
@Preview(widthDp=360,heightDp=780)
@Preview(widthDp=320,heightDp=640)
@Composable private fun HomePreview() {TvTheme {PreviewScaffold(TvUiState(step=3))}}
@Preview(widthDp=360,heightDp=780)
@Composable private fun OnboardingPreview() {TvTheme {PreviewScaffold(TvUiState())}}
@Preview(widthDp=360,heightDp=780)
@Composable private fun RemotePreview() {TvTheme {PreviewScaffold(TvUiState(step=3,tab=Feature.REMOTE,brand=TvBrand.SONY,connected=true))}}
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
    TvScaffold(state,{},{},{},{},{},{},{_,_->},{},{},{},{},{},{},null)
}
