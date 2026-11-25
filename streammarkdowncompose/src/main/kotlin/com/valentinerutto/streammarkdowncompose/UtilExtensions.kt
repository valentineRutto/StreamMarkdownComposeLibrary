package com.valentinerutto.streammarkdowncompose

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/** Collect every delta cheaply, but publish immutable full-text snapshots only at a fixed cadence. */
internal fun Flow<String>.accumulatedSnapshots(intervalMillis: Long): Flow<String> = channelFlow {
    val text = StringBuilder()
    val lock = Any()
    val producer = launch {
        this@accumulatedSnapshots.collect { delta ->
            synchronized(lock) { text.append(delta) }
        }
    }

    var lastSnapshot = ""

    while (producer.isActive) {
        delay(intervalMillis)
        val snapshot = synchronized(lock) { text.toString() }
        if (snapshot != lastSnapshot) {
            send(snapshot)
            lastSnapshot = snapshot
        }
    }

    producer.join()
    val finalSnapshot = synchronized(lock) { text.toString() }
    if (finalSnapshot != lastSnapshot) send(finalSnapshot)
}

internal fun List<String>.padTo(columnCount: Int): List<String> =
    if (size >= columnCount) take(columnCount) else this + List(columnCount - size) { "" }
