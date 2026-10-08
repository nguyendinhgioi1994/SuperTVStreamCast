package com.tuntech.supertvstreamcast.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.theme.*
import org.jetbrains.compose.resources.*
import supertvstreamcast.shared.generated.resources.*

private enum class IptvSheet { SOURCES, URL, XTREAM, STREAM, GUIDE, HELP }

@Composable internal fun PlaylistScaffold(state: TvUiState,actions: TvActions) {
    var search by rememberSaveable {mutableStateOf("")}
    var mode by rememberSaveable {mutableStateOf(0)} // 0 all, 1 favorites, 2 recent
    var group by rememberSaveable {mutableStateOf("")}
    var sheet by rememberSaveable {mutableStateOf<IptvSheet?>(null)}
    var seen by rememberSaveable {mutableStateOf(state.imported)}
    LaunchedEffect(state.imported) {if(state.imported!=seen) {seen=state.imported;sheet=null}}
    val open: (IptvSheet)->Unit = {actions.clearError();sheet=it}
    val now=rememberEpochSeconds()
    val groups=remember(state.channels) {channelGroups(state.channels)}
    val visible=remember(state.channels,state.favorites,state.recent,search,mode,group) {
        val source=if(mode==2) state.channels.associateBy{it.url}.let{byUrl -> state.recent.mapNotNull{byUrl[it]}} else state.channels
        source.filter {(search.isBlank()||it.title.contains(search,true)||it.group.contains(search,true))&&
            (mode!=1||it.url in state.favorites)&&(group.isEmpty()||it.group==group)}
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=TvDimens.Space),verticalArrangement=Arrangement.spacedBy(18.dp),contentPadding=PaddingValues(top=12.dp,bottom=20.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(Res.string.iptv),style=MaterialTheme.typography.labelSmall,color=TvColors.Coral)
                    Text(stringResource(Res.string.library),style=MaterialTheme.typography.headlineLarge)
                }
                RoundAction(stringResource(Res.string.iptv_help),Glyph.HELP,accent=TvColors.Cyan){open(IptvSheet.HELP)}
                RoundAction(stringResource(Res.string.add_playlist),Glyph.ADD,enabled=!state.busy,accent=TvColors.Coral){open(IptvSheet.SOURCES)}
            }
        }
        if(sheet==null) state.error?.let {item {ErrorNotice(it)}}
        if(state.busy) item {LinearProgressIndicator(Modifier.fillMaxWidth(),color=TvColors.Coral)}
        if(state.channels.isEmpty()) {
            item {
                CinemaPanel(Modifier.fillMaxWidth()) {
                    Artwork(Modifier.height(170.dp),Res.drawable.art_iptv)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.playlist_empty_title),style=MaterialTheme.typography.headlineSmall)
                    Text(stringResource(Res.string.playlist_empty_body),style=MaterialTheme.typography.bodyLarge,color=TvColors.Muted)
                }
            }
            item {Text(stringResource(Res.string.iptv_sources),style=MaterialTheme.typography.titleLarge)}
            item {SourceList(state,actions){it?.let(open)}}
            item {HelpCard{open(IptvSheet.HELP)}}
            item {Text(stringResource(Res.string.playlist_body),style=MaterialTheme.typography.bodySmall,color=TvColors.Muted)}
        } else {
            item {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    LibraryStat(pluralStringResource(Res.plurals.library_channel_count,state.channels.size,state.channels.size),Feature.IPTV,TvColors.Coral,Modifier.weight(1f))
                    LibraryStat(pluralStringResource(Res.plurals.library_favorite_count,state.favorites.size,state.favorites.size),Feature.HOME,TvColors.Cyan,Modifier.weight(1f))
                }
            }
            if(state.truncated) item {Text(stringResource(Res.string.library_truncated),style=MaterialTheme.typography.bodySmall,color=TvColors.Coral)}
            item {GuideBanner(state){open(IptvSheet.GUIDE)}}
            item {
                OutlinedTextField(search,{search=it},placeholder={Text(stringResource(Res.string.search))},leadingIcon={GlyphIcon(Glyph.SEARCH,color=TvColors.Muted)},
                    modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(18.dp))
            }
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(TvColors.Surface).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    LibraryFilter(stringResource(Res.string.all_channels),mode==0,Modifier.weight(1f)){mode=0}
                    LibraryFilter(stringResource(Res.string.favorites),mode==1,Modifier.weight(1f)){mode=1}
                    LibraryFilter(stringResource(Res.string.recent),mode==2,Modifier.weight(1f)){mode=2}
                }
            }
            if(groups.isNotEmpty()) item {
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    item {GroupChip(stringResource(Res.string.all_groups),group.isEmpty()){group=""}}
                    items(groups,key={it}) {name -> GroupChip(name,group==name){group=name}}
                }
            }
            if(visible.isEmpty()) item {CinemaPanel(Modifier.fillMaxWidth()) {Text(stringResource(Res.string.no_results),style=MaterialTheme.typography.bodyLarge,color=TvColors.Muted)}}
            items(visible,key={it.url}) {channel ->
                ChannelCard(channel,channel.url in state.favorites,upcoming(state.guide[channel.url],now),now,{actions.favorite(channel.url)},{actions.play(channel)})
            }
            item {Text(stringResource(Res.string.session_library_note),style=MaterialTheme.typography.bodySmall,color=TvColors.Muted)}
        }
    }
    val close={if(!state.busy)sheet=null}
    when(sheet) {
        IptvSheet.SOURCES -> IptvDialog(stringResource(Res.string.iptv_sources),Glyph.ADD,onDismiss=close) {
            SourceList(state,actions){next -> if(next==null) sheet=null else open(next)}
        }
        IptvSheet.URL -> UrlDialog(state,actions.importPlaylist,close)
        IptvSheet.XTREAM -> XtreamDialog(state,actions.importXtream,close)
        IptvSheet.STREAM -> StreamDialog(state,actions.playStream,close)
        IptvSheet.GUIDE -> GuideDialog(state,actions,close)
        IptvSheet.HELP -> IptvDialog(stringResource(Res.string.iptv_help_title),Glyph.HELP,onDismiss=close) {HelpSteps()}
        null -> Unit
    }
}
/** Import sources; `null` means a system picker opened and the caller's sheet should close. */
@Composable private fun SourceList(state: TvUiState,actions: TvActions,onOpen: (IptvSheet?)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        SourceRow(Glyph.LINK,stringResource(Res.string.source_url),stringResource(Res.string.source_url_body),!state.busy){onOpen(IptvSheet.URL)}
        SourceRow(Glyph.LOCK,stringResource(Res.string.source_xtream),stringResource(Res.string.source_xtream_body),!state.busy){onOpen(IptvSheet.XTREAM)}
        SourceRow(Glyph.FILE,stringResource(Res.string.source_file),stringResource(Res.string.source_file_body),!state.busy){actions.clearError();onOpen(null);actions.pickPlaylistFile()}
        SourceRow(Glyph.PLAY,stringResource(Res.string.source_stream),stringResource(Res.string.source_stream_body),!state.busy){onOpen(IptvSheet.STREAM)}
        SourceRow(Glyph.GUIDE,stringResource(Res.string.source_guide),stringResource(if(state.channels.isEmpty()) Res.string.source_guide_locked else Res.string.source_guide_body),
            !state.busy&&state.channels.isNotEmpty()){onOpen(IptvSheet.GUIDE)}
    }
}
@Composable private fun SourceRow(glyph: Glyph,title: String,body: String,enabled: Boolean,onClick: ()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(TvColors.Raised).border(1.dp,TvColors.Outline,RoundedCornerShape(20.dp))
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick).heightIn(min=TvDimens.Touch).padding(14.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        val accent=if(enabled) TvColors.Coral else TvColors.Muted
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha=0.12f)),contentAlignment=Alignment.Center) {
            GlyphIcon(glyph,Modifier.size(20.dp),accent)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text(title,style=MaterialTheme.typography.titleSmall,color=if(enabled) TvColors.Text else TvColors.Muted)
            Text(body,style=MaterialTheme.typography.bodySmall,color=TvColors.Muted)
        }
        GlyphIcon(Glyph.RIGHT,Modifier.size(18.dp),accent)
    }
}
@Composable private fun HelpCard(onClick: ()->Unit) {
    Card(onClick=onClick,shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=TvColors.Surface),
        border=BorderStroke(1.dp,TvColors.Cyan.copy(alpha=0.2f)),modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            GlyphIcon(Glyph.HELP,color=TvColors.Cyan)
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(stringResource(Res.string.iptv_help_title),style=MaterialTheme.typography.titleSmall)
                Text(stringResource(Res.string.iptv_help_body),style=MaterialTheme.typography.bodySmall,color=TvColors.Muted)
            }
            GlyphIcon(Glyph.RIGHT,Modifier.size(18.dp),TvColors.Cyan)
        }
    }
}
@Composable private fun GuideBanner(state: TvUiState,onClick: ()->Unit) {
    val loaded=state.guide.isNotEmpty()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TvColors.Surface).clickable(enabled=!state.busy,role=Role.Button,onClick=onClick)
        .padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        GlyphIcon(Glyph.GUIDE,Modifier.size(20.dp),if(loaded) TvColors.Cyan else TvColors.Coral)
        Text(if(loaded) pluralStringResource(Res.plurals.guide_loaded,state.guide.size,state.guide.size)
            else stringResource(if(state.guideUrl.isNotEmpty()) Res.string.guide_available else Res.string.guide_add),
            Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=TvColors.Text)
        GlyphIcon(Glyph.RIGHT,Modifier.size(18.dp),TvColors.Muted)
    }
}
@Composable private fun IptvDialog(title: String,glyph: Glyph,confirm: String?=null,confirmEnabled: Boolean=true,onConfirm: ()->Unit={},
    onDismiss: ()->Unit,content: @Composable ColumnScope.()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,shape=RoundedCornerShape(30.dp),containerColor=TvColors.Surface,
        icon={Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(TvColors.Coral.copy(alpha=0.12f)),contentAlignment=Alignment.Center){GlyphIcon(glyph,color=TvColors.Coral)}},
        title={Text(title,style=MaterialTheme.typography.headlineSmall)},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)},
        confirmButton={if(confirm!=null) TextButton(onClick=onConfirm,enabled=confirmEnabled){Text(confirm)}},
        dismissButton={TextButton(onClick=onDismiss){Text(stringResource(if(confirm==null) Res.string.close else Res.string.back))}})
}
@Composable private fun DialogStatus(state: TvUiState) {
    state.error?.let {ErrorNotice(it)}
    if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=TvColors.Coral)
}
@Composable private fun Field(value: String,onValue: (String)->Unit,label: String,enabled: Boolean,secret: Boolean=false,minLines: Int=1,
    keyboard: KeyboardType=KeyboardType.Uri) {
    OutlinedTextField(value,onValue,label={Text(label)},enabled=enabled,singleLine=minLines==1,minLines=minLines,maxLines=if(minLines==1) 1 else 5,
        shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=keyboard,autoCorrectEnabled=false),
        visualTransformation=if(secret) PasswordVisualTransformation() else VisualTransformation.None)
}
@Composable private fun UrlDialog(state: TvUiState,onImport: (String)->Unit,onDismiss: ()->Unit) {
    var input by remember {mutableStateOf("")}
    IptvDialog(stringResource(Res.string.playlist_dialog_title),Glyph.LINK,stringResource(if(state.busy) Res.string.working else Res.string.import_playlist),
        input.isNotBlank()&&!state.busy,{onImport(input)},onDismiss) {
        Text(stringResource(Res.string.playlist_dialog_hint),style=MaterialTheme.typography.bodyMedium,color=TvColors.Muted)
        Field(input,{if(it.length<=PLAYLIST_MAX_BYTES)input=it},stringResource(Res.string.playlist_input),!state.busy,minLines=3)
        if(input.trim().startsWith("http://",true)) Text(stringResource(Res.string.http_warning),style=MaterialTheme.typography.bodySmall,color=TvColors.Coral)
        DialogStatus(state)
    }
}
@Composable private fun XtreamDialog(state: TvUiState,onImport: (String,String,String)->Unit,onDismiss: ()->Unit) {
    var server by remember {mutableStateOf("")}
    var user by remember {mutableStateOf("")}
    var pass by remember {mutableStateOf("")}
    IptvDialog(stringResource(Res.string.xtream_title),Glyph.LOCK,stringResource(if(state.busy) Res.string.working else Res.string.xtream_login),
        server.isNotBlank()&&user.isNotBlank()&&pass.isNotEmpty()&&!state.busy,{onImport(server,user,pass)},onDismiss) {
        Text(stringResource(Res.string.xtream_hint),style=MaterialTheme.typography.bodyMedium,color=TvColors.Muted)
        Field(server,{server=it.trim()},stringResource(Res.string.xtream_server),!state.busy)
        Field(user,{user=it},stringResource(Res.string.xtream_username),!state.busy,keyboard=KeyboardType.Text)
        Field(pass,{pass=it},stringResource(Res.string.xtream_password),!state.busy,secret=true,keyboard=KeyboardType.Password)
        if(!server.startsWith("https://",true)&&server.isNotBlank()) Text(stringResource(Res.string.http_warning),style=MaterialTheme.typography.bodySmall,color=TvColors.Coral)
        Text(stringResource(Res.string.xtream_privacy),style=MaterialTheme.typography.bodySmall,color=TvColors.Muted)
        DialogStatus(state)
    }
}
@Composable private fun StreamDialog(state: TvUiState,onPlay: (String,String)->Unit,onDismiss: ()->Unit) {
    var url by remember {mutableStateOf("")}
    var name by remember {mutableStateOf("")}
    IptvDialog(stringResource(Res.string.stream_title),Glyph.PLAY,stringResource(Res.string.stream_play),url.isNotBlank()&&!state.busy,{onPlay(url,name)},onDismiss) {
        Text(stringResource(Res.string.stream_hint),style=MaterialTheme.typography.bodyMedium,color=TvColors.Muted)
        Field(url,{url=it.trim()},stringResource(Res.string.stream_url),!state.busy)
        Field(name,{name=it},stringResource(Res.string.stream_name),!state.busy,keyboard=KeyboardType.Text)
        DialogStatus(state)
    }
}
@Composable private fun GuideDialog(state: TvUiState,actions: TvActions,onDismiss: ()->Unit) {
    var url by remember {mutableStateOf("")}
    IptvDialog(stringResource(Res.string.guide_title),Glyph.GUIDE,stringResource(if(state.busy) Res.string.working else Res.string.guide_import),
        url.isNotBlank()&&!state.busy,{actions.importGuide(url)},onDismiss) {
        Text(stringResource(Res.string.guide_hint),style=MaterialTheme.typography.bodyMedium,color=TvColors.Muted)
        if(state.guideUrl.isNotEmpty()) OutlinedButton(onClick=actions.providerGuide,enabled=!state.busy,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            GlyphIcon(Glyph.GUIDE,Modifier.size(18.dp),TvColors.Cyan);Spacer(Modifier.width(8.dp));Text(stringResource(Res.string.guide_provider))
        }
        Field(url,{url=it.trim()},stringResource(Res.string.guide_url),!state.busy)
        OutlinedButton(onClick={actions.clearError();actions.pickGuideFile()},enabled=!state.busy,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            GlyphIcon(Glyph.FILE,Modifier.size(18.dp),TvColors.Cyan);Spacer(Modifier.width(8.dp));Text(stringResource(Res.string.guide_file))
        }
        if(url.startsWith("http://",true)) Text(stringResource(Res.string.http_warning),style=MaterialTheme.typography.bodySmall,color=TvColors.Coral)
        DialogStatus(state)
    }
}
@Composable private fun HelpSteps() {
    val steps=listOf(Res.string.help_url to Res.string.help_url_body,Res.string.help_xtream to Res.string.help_xtream_body,
        Res.string.help_file to Res.string.help_file_body,Res.string.help_stream to Res.string.help_stream_body,
        Res.string.help_guide to Res.string.help_guide_body,Res.string.help_watch to Res.string.help_watch_body)
    steps.forEachIndexed {index,(title,body) ->
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(30.dp).background(TvColors.Coral.copy(alpha=0.14f),CircleShape),contentAlignment=Alignment.Center) {
                Text("${index+1}",style=MaterialTheme.typography.labelLarge,color=TvColors.Coral)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title),style=MaterialTheme.typography.titleSmall)
                Text(stringResource(body),style=MaterialTheme.typography.bodyMedium,color=TvColors.Muted)
            }
        }
    }
    Text(stringResource(Res.string.help_legal),style=MaterialTheme.typography.bodySmall,color=TvColors.Cyan)
}
/** Wall clock for EPG progress, refreshed every 30 seconds while visible. */
@Composable internal fun rememberEpochSeconds(): Long {
    var now by remember {mutableStateOf(epochSeconds())}
    LaunchedEffect(Unit) {while(true) {kotlinx.coroutines.delay(30_000);now=epochSeconds()}}
    return now
}
/** Current programme with progress and minutes left, or the next one if nothing is airing. */
@Composable internal fun NowNext(programmes: List<Programme>,now: Long,showNext: Boolean=true) {
    val first=programmes.firstOrNull() ?: return
    if(isAiring(first,now)) {
        Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(stringResource(Res.string.guide_now,first.title),style=MaterialTheme.typography.bodySmall,color=TvColors.Cyan,maxLines=1,overflow=TextOverflow.Ellipsis)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress={((now-first.start).toFloat()/(first.stop-first.start)).coerceIn(0f,1f)},Modifier.weight(1f).height(3.dp),
                    color=TvColors.Cyan,trackColor=TvColors.Outline)
                val left=((first.stop-now+59)/60).toInt()
                Text(pluralStringResource(Res.plurals.guide_minutes_left,left,left),style=MaterialTheme.typography.labelSmall,color=TvColors.Muted)
            }
            if(showNext) programmes.getOrNull(1)?.let {Text(stringResource(Res.string.guide_next,it.title),style=MaterialTheme.typography.bodySmall,color=TvColors.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)}
        }
    } else Text(stringResource(Res.string.guide_next,first.title),style=MaterialTheme.typography.bodySmall,color=TvColors.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
}
@Composable private fun GroupChip(text: String,selected: Boolean,onClick: ()->Unit) {
    Box(Modifier.heightIn(min=40.dp).clip(RoundedCornerShape(100.dp)).background(if(selected) TvColors.Coral.copy(alpha=0.14f) else TvColors.Surface)
        .border(1.dp,if(selected) TvColors.Coral.copy(alpha=0.5f) else TvColors.Outline,RoundedCornerShape(100.dp))
        .selectable(selected=selected,role=Role.RadioButton,onClick=onClick).padding(horizontal=14.dp,vertical=10.dp),contentAlignment=Alignment.Center) {
        Text(text,style=MaterialTheme.typography.labelLarge,color=if(selected) TvColors.Coral else TvColors.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}
@Composable private fun LibraryStat(label: String,feature: Feature,accent: androidx.compose.ui.graphics.Color,modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(18.dp)).background(TvColors.Surface).padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        if(feature==Feature.HOME) GlyphIcon(Glyph.HEART,Modifier.size(18.dp),accent) else FeatureIcon(feature,Modifier.size(18.dp),accent)
        Text(label,style=MaterialTheme.typography.labelLarge,color=TvColors.Text)
    }
}
@Composable private fun LibraryFilter(text: String,selected: Boolean,modifier: Modifier,onClick: ()->Unit) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(if(selected) TvColors.Raised else androidx.compose.ui.graphics.Color.Transparent)
        .clickable(role=Role.Tab,onClick=onClick).semantics{this.selected=selected}.padding(horizontal=8.dp,vertical=13.dp),contentAlignment=Alignment.Center) {
        Text(text,style=MaterialTheme.typography.labelLarge,color=if(selected) TvColors.Cyan else TvColors.Muted)
    }
}
@Composable private fun ChannelCard(channel: Channel,favorite: Boolean,programmes: List<Programme>,now: Long,onFavorite: ()->Unit,onPlay: ()->Unit) {
    Card(onClick=onPlay,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,TvColors.Outline.copy(alpha=0.65f)),colors=CardDefaults.cardColors(containerColor=TvColors.Surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(TvColors.Coral.copy(alpha=0.1f)),contentAlignment=Alignment.Center) {
                GlyphIcon(Glyph.PLAY,Modifier.size(19.dp),TvColors.Coral)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(channel.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(channel.group.isNotBlank()) Text(channel.group,style=MaterialTheme.typography.bodySmall,color=TvColors.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                NowNext(programmes,now,showNext=false)
            }
            val label=stringResource(Res.string.favorite_action)
            IconToggleButton(checked=favorite,onCheckedChange={onFavorite()},modifier=Modifier.size(48.dp).semantics{contentDescription=label}) {
                if(favorite) Box(Modifier.size(32.dp).background(TvColors.Coral.copy(alpha=0.12f),CircleShape),contentAlignment=Alignment.Center){GlyphIcon(Glyph.HEART,Modifier.size(20.dp),TvColors.Coral)}
                else GlyphIcon(Glyph.HEART,Modifier.size(20.dp),TvColors.Muted)
            }
        }
    }
}
