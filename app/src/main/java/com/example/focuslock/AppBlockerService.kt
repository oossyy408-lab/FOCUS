package com.example.focuslock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * 화면 맨 앞에 뜬 앱을 감지해서, 잠금 중이고 허용 목록에 없으면 차단 화면을 띄웁니다.
 *
 * 화면 내용은 읽지 않고(canRetrieveWindowContent=false) 패키지 이름만 봅니다.
 */
class AppBlockerService : AccessibilityService() {

    private val prefs by lazy { LockPrefs(this) }

    /** 절대 막으면 안 되는 앱들 (런처, 시스템 UI, 전화 등) */
    private val neverBlock = mutableSetOf<String>()

    /** 런처블 앱인지 여부 캐시 — 키보드/시스템 팝업 등을 걸러내기 위함 */
    private val launchableCache = HashMap<String, Boolean>()

    private var lastBlockAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        neverBlock.clear()
        launchableCache.clear()

        neverBlock += packageName
        neverBlock += "com.android.systemui"
        neverBlock += homePackages()
        dialerPackage()?.let { neverBlock += it }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        if (!prefs.isLockActive()) return
        if (pkg in neverBlock) return
        if (prefs.isAllowed(pkg)) return
        if (!prefs.blockSettings && isSettingsPackage(pkg)) return
        if (!isLaunchable(pkg)) return // 키보드, 시스템 다이얼로그 등은 무시

        // 같은 이벤트가 연속으로 들어오는 경우 방지
        val now = SystemClock.elapsedRealtime()
        if (now - lastBlockAt < 600) return
        lastBlockAt = now

        val intent = Intent(this, BlockOverlayActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
            putExtra(BlockOverlayActivity.EXTRA_PACKAGE, pkg)
        }
        startActivity(intent)
    }

    override fun onInterrupt() = Unit

    // ---------- helpers ----------

    private fun homePackages(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
    }

    private fun dialerPackage(): String? {
        val intent = Intent(Intent.ACTION_DIAL)
        return packageManager
            .resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
    }

    private fun isSettingsPackage(pkg: String): Boolean =
        pkg == "com.android.settings" || pkg.endsWith(".settings")

    private fun isLaunchable(pkg: String): Boolean =
        launchableCache.getOrPut(pkg) {
            packageManager.getLaunchIntentForPackage(pkg) != null
        }
}
