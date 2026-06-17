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
    val platform: Platform
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
        fun fromResourceId(id: Int): Platform {
            return entries.firstOrNull { it.resourceId == id }
                ?: CODEFORCES
        }
    }
}
