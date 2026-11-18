package com.lifetracker.app.data

/**
 * Joining two phone periods that sit next to each other on the Timeline into one (you hold one and drag it onto the other).
 * No Android in the rules, so they can be tested.
 *
 * Nothing is deleted. The later period is hidden with a note of which period it went into; the Timeline folds every period
 * hidden into one into that one when it draws the day. "Separate again" only brings the hidden period back.
 */
object PhoneJoin {
    /** The reason written on a period that was taken into the one before it. The ref of that period follows after "|". */
    const val LABEL = "Joined to the phone period before it"

    private const val SEP = "|"

    fun isJoined(edit: TimelineEditEntity?): Boolean = edit != null && edit.hidden && edit.label.startsWith(LABEL)

    /** The ref ([TimelineEdits.appRef]) of the period this one went into, or null. */
    fun keeperOf(edit: TimelineEditEntity?): String? =
        if (!isJoined(edit)) null else edit?.label?.split(SEP)?.getOrNull(1)?.takeIf { it.isNotEmpty() }

    /**
     * What a join saves: the later period [dropRef] is hidden into [keepRef], and any period that was already folded into the
     * dropped one is pointed at [keepRef] instead, so it does not disappear.
     */
    fun join(keepRef: String, dropRef: String, edits: Collection<TimelineEditEntity>, nowMs: Long): List<TimelineEditEntity> {
        if (keepRef == dropRef) return emptyList()
        val appEdits = edits.filter { it.kind == TimelineEdits.APP }
        val old = appEdits.firstOrNull { it.refId == dropRef }
        val out = ArrayList<TimelineEditEntity>()
        out += TimelineEdits.edit(
            TimelineEdits.APP, dropRef,
            startMs = old?.startMs ?: -1L, endMs = old?.endMs ?: -1L, activityId = old?.activityId.orEmpty(),
            hidden = true, now = nowMs, label = LABEL + SEP + keepRef,
        )
        for (e in appEdits) {
            if (keeperOf(e) == dropRef) out += e.copy(label = LABEL + SEP + keepRef, updatedMs = nowMs)
        }
        return out
    }

    /** Brings back every period that was folded into [keepRef]. */
    fun separate(keepRef: String, edits: Collection<TimelineEditEntity>, nowMs: Long): List<TimelineEditEntity> =
        edits.filter { keeperOf(it) == keepRef && it.kind == TimelineEdits.APP }
            .map { it.copy(hidden = false, label = "", updatedMs = nowMs) }

    /** One period out of [keeper] and the periods [others] that were joined into it. */
    fun merge(keeper: UsageDays.Block, others: List<UsageDays.Block>): UsageDays.Block {
        if (others.isEmpty()) return keeper
        val all = listOf(keeper) + others
        val uses = all.flatMap { it.apps }.groupBy { it.pkg }.map { (pkg, list) ->
            UsageDays.AppUse(pkg, list.first().label, list.first().activityId, list.sumOf { it.activeMs }, list.sumOf { it.opens })
        }.sortedWith(compareByDescending<UsageDays.AppUse> { it.activeMs }.thenBy { it.pkg })
        val title = if (uses.size == 1) {
            uses.single().label
        } else {
            uses.take(3).joinToString(" + ") { it.label } + if (uses.size > 3) " + ${uses.size - 3} more" else ""
        }
        val appSpans = LinkedHashMap<String, List<Pair<Long, Long>>>()
        for (b in all) for ((pkg, list) in b.appSpans) appSpans[pkg] = ((appSpans[pkg] ?: emptyList()) + list).sortedBy { it.first }
        val credit = HashMap<String, Long>()
        for (b in all) for ((id, ms) in b.credit) credit[id] = (credit[id] ?: 0L) + ms
        return keeper.copy(
            label = title,
            activityId = keeper.activityId.ifEmpty { if (uses.size > 1) UsageDays.MIXED_ACTIVITY_ID else "" },
            startMs = all.minOf { it.startMs },
            endMs = all.maxOf { it.endMs },
            activeMs = all.sumOf { it.activeMs },
            opens = all.sumOf { it.opens },
            spans = all.flatMap { it.spans }.sortedBy { it.first },
            apps = uses,
            comboId = "",
            appSpans = appSpans,
            credit = credit,
            joined = all.drop(1).flatMap { listOf(TimelineEdits.appRef(it.pkg, it.refStartMs)) } + keeper.joined,
        )
    }
}
