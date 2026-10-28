package com.blacksingularities.opencall.data

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers

/**
 * Systemowa lista zablokowanych numerów (BlockedNumberContract). Dostęp ma tylko domyślna aplikacja
 * telefonu lub SMS – dlatego każda operacja jest zabezpieczona, a lista bywa pusta przed nadaniem roli.
 */
class BlockRepo(private val context: Context) {
    private val cr get() = context.contentResolver

    fun canAccess(): Boolean = runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false)

    fun list(): List<String> = runCatching {
        cr.query(BlockedNumbers.CONTENT_URI, arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER), null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) c.getString(0)?.let { add(it) } }
        }.orEmpty()
    }.getOrDefault(emptyList())

    fun isBlocked(number: String): Boolean = runCatching { BlockedNumberContract.isBlocked(context, number) }.getOrDefault(false)

    fun block(number: String): Boolean = runCatching {
        if (isBlocked(number)) return true
        cr.insert(BlockedNumbers.CONTENT_URI, ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) }) != null
    }.getOrDefault(false)

    fun unblock(number: String): Boolean = runCatching {
        BlockedNumberContract.unblock(context, number) > 0
    }.getOrDefault(false)
}
