package com.ghhub.app.apps

import com.ghhub.app.core.UpdateState

/** 被管理的 GitHub 应用（只记录「从本应用下载过的」）。 */
data class ManagedApp(
    val id: String,                 // 产品代码 / 登记编码
    val name: String,
    val repo: String,               // owner/repo
    val version: String,
    val fingerprint: String,
    val versionJsonUrl: String,
    val apkAssetPattern: String = ".*\\.apk",
    var state: UpdateState = UpdateState.Idle,
    var lastCheckedAt: Long = 0L
)

/** 四行状态语义（对应设计稿 D 屏：可选/强制/最新/已跳过静默）。 */
enum class AppRowStatus { OPTIONAL, FORCED, LATEST, SKIPPED, UNKNOWN }

fun ManagedApp.rowStatus(isSkipped: Boolean): AppRowStatus = when (state) {
    is UpdateState.OptionalUpdate -> AppRowStatus.OPTIONAL
    is UpdateState.ForcedUpdate -> AppRowStatus.FORCED
    is UpdateState.NoUpdate -> if (isSkipped) AppRowStatus.SKIPPED else AppRowStatus.LATEST
    is UpdateState.Unknown -> AppRowStatus.UNKNOWN
    else -> AppRowStatus.LATEST
}
