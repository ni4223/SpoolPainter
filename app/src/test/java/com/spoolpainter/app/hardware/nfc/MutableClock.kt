package com.spoolpainter.app.hardware.nfc

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

internal class MutableClock(var nowMs: Long = 0L) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(nowMs)
}
