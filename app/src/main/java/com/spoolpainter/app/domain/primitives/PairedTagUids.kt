package com.spoolpainter.app.domain.primitives

import android.util.Log
import com.spoolpainter.app.domain.models.SpoolmanSpool

/**
 * Union of the UIDs paired with a spool across both tag-storage sources:
 * the legacy `extra.card_uids` string (TagStorageMode BOTH / LEGACY_ONLY,
 * and all tags on servers < 0.27) plus the native `spool.tags` list
 * (server >= 0.27, populated in BOTH / NATIVE_ONLY modes where the legacy
 * mirror may be absent entirely). Mode-independent by construction: on old
 * servers or LEGACY mode the tags list is empty and the result is
 * bit-identical to a pure legacy read.
 *
 * Deduped by normalised uppercase hex ([CardUid.normaliseHex]). Legacy
 * entries come first in decode order — so [firstOrNull] preserves the
 * historical prefill when both sources agree — with native-only entries
 * appended after in server order. Invalid native tag UIDs are skipped
 * defensively, mirroring [ExtraCardUidsCodec.decode].
 */
object PairedTagUids {

    private const val TAG = "PairedTagUids"

    fun of(spool: SpoolmanSpool): List<CardUid> {
        val legacy = ExtraCardUidsCodec.decode(spool.extra?.get("card_uids") ?: "")
        // Defensive: `tags` defaults to emptyList() but a missing list on an
        // old-server payload must read as "no native tags", not null.
        val nativeTags = spool.tags.orEmpty()
        if (nativeTags.isEmpty()) return legacy
        val seen = legacy.mapTo(mutableSetOf()) { it.hex }
        val native = nativeTags.mapNotNull { tag ->
            runCatching { CardUid(CardUid.normaliseHex(tag.uid)) }.getOrElse {
                Log.w(TAG, "skipped invalid tag uid: ${tag.uid}")
                null
            }
        }
        return legacy + native.filter { it.hex !in seen }
    }
}
