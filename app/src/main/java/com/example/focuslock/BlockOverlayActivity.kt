package com.example.focuslock

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 허용되지 않은 앱을 열었을 때 뜨는 전체 화면.
 * 뒤로가기를 눌러도 그 앱으로 돌아가지 않고 홈으로 나갑니다.
 */
class BlockOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = LockPrefs(this)
        val blockedLabel = intent.getStringExtra(EXTRA_PACKAGE)?.let { appLabel(it) }

        setContent {
            BlockScreen(
                blockedLabel = blockedLabel,
                remainingProvider = { prefs.remainingMillis() },
                onExit = { goHome() }
            )
        }
    }

    private fun appLabel(pkg: String): String? = runCatching {
        val info = packageManager.getApplicationInfo(pkg, 0)
        packageManager.getApplicationLabel(info).toString()
    }.getOrNull()

    private fun goHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
    }

    companion object {
        const val EXTRA_PACKAGE = "extra_package"
    }
}

@Composable
private fun BlockScreen(
    blockedLabel: String?,
    remainingProvider: () -> Long,
    onExit: () -> Unit
) {
    var remaining by remember { mutableLongStateOf(remainingProvider()) }

    LaunchedEffect(Unit) {
        while (true) {
            remaining = remainingProvider()
            if (remaining <= 0L) {
                onExit()
                break
            }
            delay(1000)
        }
    }

    // 뒤로가기로 차단된 앱에 되돌아가지 못하게 막음
    BackHandler { onExit() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111827)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "🔒",
                fontSize = 56.sp
            )
            Text(
                text = if (blockedLabel != null) "$blockedLabel 은(는) 지금 잠겨 있어요" else "지금은 잠겨 있어요",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                text = formatRemaining(remaining),
                color = Color(0xFF93C5FD),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = "남았습니다",
                color = Color(0xFF9CA3AF),
                fontSize = 14.sp
            )
            Button(
                onClick = onExit,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                modifier = Modifier.padding(top = 32.dp)
            ) {
                Text("홈으로 나가기", color = Color.White)
            }
        }
    }
}

private fun formatRemaining(millis: Long): String {
    val total = millis / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}
