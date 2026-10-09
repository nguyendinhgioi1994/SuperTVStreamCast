package com.tuntech.supertvstreamcast.extension

import co.touchlab.kermit.Logger
import dev.gitlive.firebase.remoteconfig.FirebaseRemoteConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.jetbrains.compose.resources.ExperimentalResourceApi
import shared.resources.Res

suspend fun FirebaseRemoteConfig.setDefaultsAsync() {
    val defaults = readFirebaseDefaultsJson()
    setDefaults(defaults = defaults)
}

@OptIn(ExperimentalResourceApi::class)
private suspend fun readFirebaseDefaultsJson(): Array<Pair<String, Any>> {
    try {
        val jsonString = Res.readBytes("files/remote_config_defaults.json").decodeToString()
        val jsonObject = Json.parseToJsonElement(jsonString).jsonObject
        return jsonObject.entries.map { (key, value) ->
            key to convertJsonElementToAny(value)
        }.toTypedArray()
    } catch (e: Exception) {
        Logger.w("RemoteConfig", e) { "Failed to read remote config defaults" }
    }
    return emptyArray()
}

/** Remote Config stores scalars natively and everything else as its JSON string. */
private fun convertJsonElementToAny(element: JsonElement): Any {
    if (element !is JsonPrimitive) return element.toString()
    return when {
        element.isString -> element.content
        element.booleanOrNull != null -> element.boolean
        element.longOrNull != null -> element.long
        element.doubleOrNull != null -> element.double
        else -> element.content
    }
}
