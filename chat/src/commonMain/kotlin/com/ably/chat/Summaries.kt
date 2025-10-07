package com.ably.chat

import com.ably.chat.json.JsonValue
import io.ably.lib.types.Summary

/**
 * The summary entry for aggregated annotations that use the flag.v1
 * aggregation method; also the per-name value for some other aggregation methods.
 */
public class SummaryClientIdList internal constructor(
    /**
     * The sum of the counts from all clients who have published an annotation with this name
     */
    public val total: Int, // TM7c1a

    /**
     * A list of the clientIds of all clients who have published an annotation with this name (or
     * type, depending on context).
     */
    public val clientIds: List<String>, // TM7

    /**
     * Whether the list of clientIds has been clipped due to exceeding the maximum number of
     * clients.
     */
    public val clipped: Boolean, // TM7c1c
) {
    override fun toString(): String = "{total=$total, clientIds=$clientIds, clipped=$clipped}"
}

/**
 * The per-name value for the multiple.v1 aggregation method.
 */
public class SummaryClientIdCounts internal constructor(
    /**
     * The sum of the counts from all clients who have published an annotation with this name
     */
    public val total: Int, // TM7d1a

    /**
     * A list of the clientIds of all clients who have published an annotation with this
     * name, and the count each of them have contributed.
     */
    public val clientIds: Map<String, Int>, // TM7d1b

    /**
     * The sum of the counts from all unidentified clients who have published an annotation with this
     * name, and so who are not included in the clientIds list
     */
    public val totalUnidentified: Int, // TM7d1d

    /**
     * Whether the list of clientIds has been clipped due to exceeding the maximum number of
     * clients.
     */
    public val clipped: Boolean, // TM7d1c

    /**
     * The number of distinct identified clients who have published an annotation with this name.
     */
    public val totalClientIds: Int, // TM7d1
) {
    override fun toString(): String =
        "{total=$total, clientIds=$clientIds, totalUnidentified=$totalUnidentified, clipped=$clipped, totalClientIds=$totalClientIds}"
}

internal fun parseSummaryUniqueV1(jsonObject: JsonValue?): Map<String, SummaryClientIdList> {
    val gsonObject = jsonObject?.jsonObjectOrNull()?.toGson()?.asJsonObject ?: return mapOf()
    val summary = Summary.asSummaryUniqueV1(gsonObject)
    return summary.mapValues {
        SummaryClientIdList(
            total = it.value.total,
            clientIds = it.value.clientIds ?: listOf(),
            clipped = it.value.clipped,
        )
    }
}

internal fun parseSummaryDistinctV1(jsonObject: JsonValue?): Map<String, SummaryClientIdList> {
    val gsonObject = jsonObject?.jsonObjectOrNull()?.toGson()?.asJsonObject ?: return mapOf()
    val summary = Summary.asSummaryDistinctV1(gsonObject)
    return summary.mapValues {
        SummaryClientIdList(
            total = it.value.total,
            clientIds = it.value.clientIds ?: listOf(),
            clipped = it.value.clipped,
        )
    }
}

internal fun parseSummaryMultipleV1(jsonObject: JsonValue?): Map<String, SummaryClientIdCounts> {
    val gsonObject = jsonObject?.jsonObjectOrNull()?.toGson()?.asJsonObject ?: return mapOf()
    val summary = Summary.asSummaryMultipleV1(gsonObject)
    return summary.mapValues {
        SummaryClientIdCounts(
            total = it.value.total,
            clientIds = it.value.clientIds ?: mapOf(),
            clipped = it.value.clipped,
            totalUnidentified = it.value.totalUnidentified,
            totalClientIds = it.value.totalClientIds,
        )
    }
}
