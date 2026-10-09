package com.tuntech.supertvstreamcast.ui.iptv

import com.tuntech.supertvstreamcast.domain.*

/** Callbacks from the IPTV screens to [IptvViewModel]; screens keep only local form and filter state. */
class IptvActions(
    val back: () -> Unit = {}, val clearError: () -> Unit = {}, val clearNotice: () -> Unit = {}, val cancelImport: () -> Unit = {},
    val open: (String) -> Unit = {}, val refresh: (String) -> Unit = {}, val rename: (String, String) -> Unit = { _, _ -> },
    val refreshEvery: (String, Int) -> Unit = { _, _ -> }, val delete: (String) -> Unit = {}, val share: (IptvSource) -> Unit = {},
    /** Playlist link or pasted content, then an optional name. */
    val addPlaylist: (String, String) -> Unit = { _, _ -> }, val pickPlaylistFile: () -> Unit = {},
    /** Server, username, password, optional name. */
    val addXtream: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    val addStream: (String, String) -> Unit = { _, _ -> },
    val acceptImport: () -> Unit = {}, val dismissImport: () -> Unit = {},
    val importGuide: (String) -> Unit = {}, val pickGuideFile: () -> Unit = {},
    /** The channel and the list it was picked from. */
    val play: (Channel, List<Channel>) -> Unit = { _, _ -> }, val zap: (Int) -> Unit = {},
    val favorite: (Channel) -> Unit = {}, val hide: (Channel, Boolean) -> Unit = { _, _ -> },
    val openLibrary: (LibraryList) -> Unit = {}, val clear: (LibraryList) -> Unit = {}, val openProgrammes: (Channel) -> Unit = {},
    val playEpisode: (Episode) -> Unit = {}, val retrySeries: (Channel) -> Unit = {},
    val submitPasscode: (String) -> Unit = {}, val changePasscode: () -> Unit = {},
    val closePlayer: () -> Unit = {}, val expand: () -> Unit = {},
    val openGuide: () -> Unit = {}, val openScanner: () -> Unit = {}, val scanned: (String) -> Unit = {},
    /** Channel, programme and minutes before its start. */
    val remind: (Channel, Programme, Int) -> Unit = { _, _, _ -> }, val unremind: (Int) -> Unit = {},
    val progress: (String, Long, Long) -> Unit = { _, _, _ -> }, val ended: () -> Unit = {}, val playerError: () -> Unit = {},
)
