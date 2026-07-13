package com.example.contesttracker

import com.google.gson.annotations.SerializedName

data class ContestResponse(
    @SerializedName("objects")
    val contests: List<ContestModel> = emptyList()
)

data class ContestModel(
    @SerializedName("id")
    val id: Long,
    @SerializedName("event")
    val name: String,
    @SerializedName("resource_id")
    val resourceId: Int,
    @SerializedName("start")
    val start: String,
    @SerializedName("end")
    val end: String?,
    @SerializedName("duration")
    val durationSeconds: Long,
    @SerializedName("href")
    val url: String?
) {
    /**
     * Returns the matching [Platform], or null if [resourceId] is not
     * one of the four supported platforms.
     *
     * Callers that receive null should discard the contest rather than
     * display it with incorrect metadata. Filtering happens at the
     * repository layer (see [ContestRepository]) so adapters and the
     * scheduler never see an unclassified contest.
     */
    val platform: Platform?
        get() = Platform.fromResourceId(resourceId)
}

enum class Platform(
    val resourceId: Int,
    val displayName: String,
    val logoResId: Int,
    val colorResId: Int
) {
    CODEFORCES(1, "Codeforces", R.drawable.ic_codeforces, R.color.accent_purple),
    LEETCODE(102, "LeetCode", R.drawable.ic_leetcode, R.color.live_green),
    CODECHEF(2, "CodeChef", R.drawable.ic_codechef, R.color.accent_purple),
    ATCODER(93, "AtCoder", R.drawable.ic_atcoder, R.color.accent_purple);

    companion object {
        /**
         * Returns the [Platform] matching [id], or **null** if [id] is not
         * one of the four supported resource IDs.
         *
         * A null return means the contest's platform is unknown — it must be
         * excluded from display, scheduling, and notification preferences.
         * Defaulting to a real platform would produce wrong logos, names,
         * and incorrect notification-preference lookups.
         */
        fun fromResourceId(id: Int): Platform? =
            entries.firstOrNull { it.resourceId == id }
    }
}
