package com.tuntech.supertvstreamcast.constant

import org.koin.core.qualifier.StringQualifier

object KoinQualifier {
    val AppScope = StringQualifier("AppScope")
    val AppSettingDataStore = StringQualifier("AppSettingDataStore")
}
