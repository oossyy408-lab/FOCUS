package com.example.focuslock

import android.content.Context

/**
 * 잠금 상태를 저장하는 아주 단순한 저장소.
 * - lockEndTime: 잠금이 끝나는 시각 (epoch millis). 0이면 잠금 없음.
 * - allowedPackages: 잠금 중에도 사용할 수 있는 앱 패키지 목록.
 */
class LockPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("focus_lock", Context.MODE_PRIVATE)

    var lockEndTime: Long
        get() = sp.getLong(KEY_END, 0L)
        set(value) = sp.edit().putLong(KEY_END, value).apply()

    var allowedPackages: Set<String>
        get() = sp.getStringSet(KEY_ALLOWED, emptySet()) ?: emptySet()
        set(value) = sp.edit().putStringSet(KEY_ALLOWED, value).apply()

    /** true면 설정 앱까지 막습니다. 기본값 false (탈출구를 남겨둠) */
    var blockSettings: Boolean
        get() = sp.getBoolean(KEY_BLOCK_SETTINGS, false)
        set(value) = sp.edit().putBoolean(KEY_BLOCK_SETTINGS, value).apply()

    fun isLockActive(): Boolean = System.currentTimeMillis() < lockEndTime

    fun remainingMillis(): Long = (lockEndTime - System.currentTimeMillis()).coerceAtLeast(0L)

    fun isAllowed(pkg: String): Boolean = pkg in allowedPackages

    fun startLock(endTimeMillis: Long, allowed: Set<String>) {
        sp.edit()
            .putLong(KEY_END, endTimeMillis)
            .putStringSet(KEY_ALLOWED, allowed)
            .apply()
    }

    fun cancelLock() {
        sp.edit().putLong(KEY_END, 0L).apply()
    }

    companion object {
        private const val KEY_END = "lock_end_time"
        private const val KEY_ALLOWED = "allowed_packages"
        private const val KEY_BLOCK_SETTINGS = "block_settings"
    }
}
