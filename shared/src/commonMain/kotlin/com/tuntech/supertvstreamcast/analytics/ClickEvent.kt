package com.tuntech.supertvstreamcast.analytics

import com.tuntech.mmp.MmpManager

/**
 * Every user tap is logged as `ec_<area>_<element>` ("event click") so analytics, sales and
 * marketing can follow which buttons people use; the full list is in docs/context/analytics-events.md.
 *
 * Parameters are short enum-like values only (brand, tab, screen, plan id, …). Never pass an IP,
 * PSK, pairing code, passcode, playlist URL/token, Xtream login, channel title or typed text.
 */
object ClickEvent {
    fun log(name: String, vararg params: Pair<String, Any?>) {
        MmpManager.shared?.trackEvent(clickEventName(name), clickParams(params.asList()))
    }
}

/** [action] that logs [name] first; for passing straight to an `onClick`. */
fun tracked(name: String, vararg params: Pair<String, Any?>, action: () -> Unit): () -> Unit = {
    ClickEvent.log(name, *params)
    action()
}

/** Firebase allows at most this many characters in an event name, and in a parameter value. */
internal const val EVENT_NAME_MAX = 40
internal const val PARAM_VALUE_MAX = 100

/** `ec_` plus [name] in snake case: lower case, anything but letters and digits becomes `_`, cut to [EVENT_NAME_MAX]. */
internal fun clickEventName(name: String): String =
    ("ec_" + name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')).take(EVENT_NAME_MAX).trimEnd('_')

/** Enums by lower-case name, other values as text; nulls are left out. */
internal fun clickParams(params: List<Pair<String, Any?>>): Map<String, String> =
    params.mapNotNull { (key, value) ->
        val text = when (value) {
            null -> return@mapNotNull null
            is Enum<*> -> value.name.lowercase()
            else -> value.toString()
        }
        key to text.take(PARAM_VALUE_MAX)
    }.toMap()
