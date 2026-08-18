package com.example.focuslock

import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar

data class AppInfo(val packageName: String, val label: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    val prefs = remember { LockPrefs(context) }

    var accessibilityOn by remember { mutableStateOf(isAccessibilityEnabled(context)) }
    var remaining by remember { mutableLongStateOf(prefs.remainingMillis()) }
    var hour by remember { mutableStateOf(22) }
    var minute by remember { mutableStateOf(0) }
    var blockSettings by remember { mutableStateOf(prefs.blockSettings) }
    var showCancelDialog by remember { mutableStateOf(false) }

    val apps = remember { mutableStateListOf<AppInfo>() }
    val selected = remember { mutableStateListOf<String>().apply { addAll(prefs.allowedPackages) } }

    // 설정에서 돌아왔을 때 권한 상태 갱신
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityOn = isAccessibilityEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        apps.addAll(withContext(Dispatchers.IO) { loadLaunchableApps(context) })
    }

    LaunchedEffect(Unit) {
        while (true) {
            remaining = prefs.remainingMillis()
            delay(1000)
        }
    }

    val locked = remaining > 0

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("집중 잠금", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "정해둔 시간까지, 고른 앱만 빼고 전부 잠급니다.",
                fontSize = 14.sp,
                color = Color(0xFF6B7280)
            )

            Spacer(Modifier.height(16.dp))

            if (!accessibilityOn) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("접근성 권한이 필요합니다", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "설정 > 접근성 > 설치된 앱 > '집중 잠금 서비스'를 켜주세요. " +
                                "어떤 앱이 열려 있는지 확인하는 데만 사용됩니다.",
                            fontSize = 13.sp,
                            color = Color(0xFF6B7280)
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) { Text("접근성 설정 열기") }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (locked) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("잠금 중", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text(
                            formatRemaining(remaining) + " 남음",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { showCancelDialog = true }) {
                            Text("잠금 해제")
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("종료 시각", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m -> hour = h; minute = m },
                            hour, minute, true
                        ).show()
                    }) {
                        Text(String.format("%02d:%02d", hour, minute), fontSize = 18.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "지금보다 이른 시각을 고르면 내일 그 시각까지 잠깁니다.",
                    fontSize = 12.sp,
                    color = Color(0xFF9CA3AF)
                )

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = blockSettings, onCheckedChange = {
                        blockSettings = it
                        prefs.blockSettings = it
                    })
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("설정 앱도 잠그기", fontSize = 14.sp)
                        Text(
                            "켜면 도중에 잠금을 풀기 매우 어려워집니다.",
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        prefs.startLock(computeEndTime(hour, minute), selected.toSet())
                        remaining = prefs.remainingMillis()
                    },
                    enabled = accessibilityOn,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("잠금 시작 (허용 ${selected.size}개)")
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("잠금 중에도 쓸 앱", fontWeight = FontWeight.SemiBold)
            Text(
                "전화·홈 화면은 항상 열립니다.",
                fontSize = 12.sp,
                color = Color(0xFF9CA3AF)
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(apps, key = { it.packageName }) { app ->
                val checked = app.packageName in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (checked) Color(0xFFEFF6FF) else Color.Transparent)
                        .clickable(enabled = !locked) {
                            if (checked) selected.remove(app.packageName)
                            else selected.add(app.packageName)
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = !locked)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(app.label, fontSize = 15.sp)
                        Text(app.packageName, fontSize = 11.sp, color = Color(0xFF9CA3AF))
                    }
                }
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("잠금을 해제할까요?") },
            text = { Text("지금 해제하면 남은 시간이 사라집니다.") },
            confirmButton = {
                TextButton(onClick = {
                    prefs.cancelLock()
                    remaining = 0
                    showCancelDialog = false
                }) { Text("해제") }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) { Text("계속 잠금") }
            }
        )
    }
}

// ---------- helpers ----------

private fun loadLaunchableApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .sortedBy { it.label }
}

private fun computeEndTime(hour: Int, minute: Int): Long {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= System.currentTimeMillis()) {
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return cal.timeInMillis
}

private fun isAccessibilityEnabled(context: Context): Boolean {
    val component = ComponentName(context, AppBlockerService::class.java)
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(':').any {
        it.equals(component.flattenToString(), ignoreCase = true) ||
            it.equals(component.flattenToShortString(), ignoreCase = true)
    }
}

private fun formatRemaining(millis: Long): String {
    val total = millis / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d시간 %02d분 %02d초", h, m, s)
    else String.format("%d분 %02d초", m, s)
}
