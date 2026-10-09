package com.tuntech.supertvstreamcast.data

import coil3.PlatformContext

/** App-private directory of the IPTV library ([IptvStore]). It is not part of device or cloud backups. */
expect fun iptvDirectory(context: PlatformContext): String
/** Keystore / Keychain store for the library key, separate from the remote-control pairing secrets. */
expect fun iptvSecretStore(context: PlatformContext): com.tuntech.supertvstreamcast.platform.SecretStore
