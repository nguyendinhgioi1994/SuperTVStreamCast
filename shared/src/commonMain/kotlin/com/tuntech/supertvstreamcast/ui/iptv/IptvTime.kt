package com.tuntech.supertvstreamcast.ui.iptv

import com.tuntech.common.util.DateTimeFormatStyle
import com.tuntech.common.util.DateUtil
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
private fun local(epochSeconds: Long) = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(TimeZone.currentSystemDefault())
/** Time of day in the device's time zone, locale and 12/24-hour setting. */
internal fun formatClock(epochSeconds: Long): String = DateUtil.formatTime(local(epochSeconds))
internal fun formatDay(epochSeconds: Long): String = DateUtil.formatDate(local(epochSeconds).date, DateTimeFormatStyle.MEDIUM)
