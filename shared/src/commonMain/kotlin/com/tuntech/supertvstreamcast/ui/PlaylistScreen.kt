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
import androidx.compose.ui.unit.dp
import com.tuntech.supertvstreamcast.domain.*
import com.tuntech.supertvstreamcast.theme.*
import org.jetbrains.compose.resources.*
import supertvstreamcast.shared.generated.resources.*

@Composable internal fun PlaylistScaffold(state: TvUiState,onImport: (String)->Unit,onFavorite: (String)->Unit,onPlay: (Channel)->Unit) {
    var input by remember {mutableStateOf("")}
    var search by rememberSaveable {mutableStateOf("")}
    var mode by rememberSaveable {mutableStateOf(0)} // 0 all, 1 favorites, 2 recent
    var group by rememberSaveable {mutableStateOf("")}
    var importOpen by rememberSaveable {mutableStateOf(false)}
    var submitted by remember {mutableStateOf(false)}
    LaunchedEffect(state.busy,state.error,state.channels) {
        if(submitted&&!state.busy) {
            if(state.error==null&&state.channels.isNotEmpty()) {importOpen=false;input=""}
            submitted=false
        }
    }
    val groups=remember(state.channels) {channelGroups(state.channels)}
    val visible=remember(state.channels,state.favorites,state.recent,search,mode,group) {
        val source=if(mode==2) state.channels.associateBy{it.url}.let{byUrl -> state.recent.mapNotNull{byUrl[it]}} else state.channels
        source.filter {(search.isBlank()||it.title.contains(search,true)||it.group.contains(search,true))&&
            (mode!=1||it.url in state.favorites)&&(group.isEmpty()||it.group==group)}
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=TvDimens.Space),verticalArrangement=Arrangement.spacedBy(18.dp),contentPadding=PaddingValues(top=12.dp,bottom=20.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(Res.string.iptv),style=MaterialTheme.typography.labelSmall,color=TvColors.Coral)
                    Text(stringResource(Res.string.library),style=MaterialTheme.typography.headlineLarge)
                }
                RoundAction(stringResource(Res.string.add_playlist),Glyph.ADD,enabled=!state.busy,accent=TvColors.Coral){importOpen=true}
            }
        }
        state.error?.let {item {ErrorNotice(it)}}
        if(state.busy) item {LinearProgressIndicator(Modifier.fillMaxWidth(),color=TvColors.Coral)}
        if(state.channels.isEmpty()) {
            item {
                CinemaPanel(Modifier.fillMaxWidth()) {
                    Artwork(Modifier.height(180.dp),Res.drawable.art_iptv)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.playlist_empty_title),style=MaterialTheme.typography.headlineSmall)
                    Text(stringResource(Res.string.playlist_empty_body),style=MaterialTheme.typography.bodyLarge,color=TvColors.Muted)
                    Spacer(Modifier.height(4.dp))
                    PrimaryCta(stringResource(Res.string.add_playlist),enabled=!state.busy,glyph=Glyph.ADD){importOpen=true}
                }
            }
            item {Text(stringResource(Res.string.playlist_body),style=MaterialTheme.typography.bodySmall,color=TvColors.Muted)}
        } else {
            item {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    LibraryStat(pluralStringResource(Res.plurals.library_channel_count,state.channels.size,state.channels.size),Feature.IPTV,TvColors.Coral,Modifier.weight(1f))
                    LibraryStat(pluralStringResource(Res.plurals.library_favorite_count,state.favorites.size,state.favorites.size),Feature.HOME,TvColors.Cyan,Modifier.weight(1f))
                }
            }
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
            items(visible,key={it.url}) {channel ->ChannelCard(channel,channel.url in state.favorites,{onFavorite(channel.url)},{onPlay(channel)})}
            item {Text(stringResource(Res.string.session_library_note),style=MaterialTheme.typography.bodySmall,color=TvColors.Muted)}
        }
    }
    if(importOpen) AlertDialog(onDismissRequest={if(!state.busy)importOpen=false},shape=RoundedCornerShape(30.dp),containerColor=TvColors.Surface,
        icon={IconBubble(Feature.IPTV,TvColors.Coral)},title={Text(stringResource(Res.string.playlist_dialog_title),style=MaterialTheme.typography.headlineSmall)},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(stringResource(Res.string.playlist_dialog_hint),style=MaterialTheme.typography.bodyMedium,color=TvColors.Muted)
            OutlinedTextField(input,{if(it.length<=2_000_000)input=it},placeholder={Text(stringResource(Res.string.playlist_input))},
                shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth(),minLines=3,maxLines=5,enabled=!state.busy)
            state.error?.let {ErrorNotice(it)}
            if(state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }},confirmButton={TextButton(onClick={submitted=true;onImport(input)},enabled=input.isNotBlank()&&!state.busy){Text(stringResource(if(state.busy) Res.string.working else Res.string.import_playlist))}},
        dismissButton={TextButton(onClick={importOpen=false},enabled=!state.busy){Text(stringResource(Res.string.back))}})
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
@Composable private fun ChannelCard(channel: Channel,favorite: Boolean,onFavorite: ()->Unit,onPlay: ()->Unit) {
    Card(onClick=onPlay,shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,TvColors.Outline.copy(alpha=0.65f)),colors=CardDefaults.cardColors(containerColor=TvColors.Surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(TvColors.Coral.copy(alpha=0.1f)),contentAlignment=Alignment.Center) {
                GlyphIcon(Glyph.PLAY,Modifier.size(19.dp),TvColors.Coral)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(channel.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                if(channel.group.isNotBlank()) Text(channel.group,style=MaterialTheme.typography.bodySmall,color=TvColors.Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            val label=stringResource(Res.string.favorite_action)
            IconToggleButton(checked=favorite,onCheckedChange={onFavorite()},modifier=Modifier.size(48.dp).semantics{contentDescription=label}) {
                if(favorite) Box(Modifier.size(32.dp).background(TvColors.Coral.copy(alpha=0.12f),CircleShape),contentAlignment=Alignment.Center){GlyphIcon(Glyph.HEART,Modifier.size(20.dp),TvColors.Coral)}
                else GlyphIcon(Glyph.HEART,Modifier.size(20.dp),TvColors.Muted)
            }
        }
    }
}
