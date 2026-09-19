/*
 * Copyright (C) 2026 FingerprintAccuracyEnhancer contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This file contains code adapted in part from Root Activity Launcher:
 * https://github.com/zacharee/RootActivityLauncher
 *
 * See LICENSE and UPSTREAM-SOURCE-NOTICE.md for details.
 */

package com.userapp.fplauncher

import android.app.AlertDialog
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import org.lsposed.hiddenapibypass.HiddenApiBypass

private const val FP_PACKAGE = "com.samsung.android.biometrics.app.setting"
private const val FP_ACTIVITY =
    "com.samsung.android.biometrics.app.setting.fingerprint.enroll.FingerprintUpdateActivity"

private const val SETTINGS_PACKAGE = "com.android.settings"
private const val SETTINGS_ACTIVITY =
    "com.android.settings.Settings\$FingerprintSettingsActivity"

private const val WSS = "android.permission.WRITE_SECURE_SETTINGS"
private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
private const val SHIZUKU_REQUEST_CODE = 0x51F1

private const val PREFS = "launcher_prefs"
private const val KEY_SHIZUKU_PROMPTED = "shizuku_prompted"
private const val KEY_SETUP_COMPLETE = "setup_complete"

private const val ASSISTANT_KEY = "assistant"
private const val RESTORE_DELAY_MS = 700L

// Android 16 QPR2 / minor SDK 36.1. Android uses SDK_INT_FULL because SDK_INT
// remains 36 for Android 16 minor releases.
private const val MIN_ANDROID_SDK_FULL = 3_600_001

// Samsung's ro.build.version.oneui convention: One UI 8.5 is 80500.
private const val MIN_ONE_UI_VERSION = 80_500

class MainActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var firstRunDialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isSupportedPlatform()) {
            applySystemBarAppearance(isDark = false)
            setContent {
                UnsupportedPlatformScreen()
            }
            return
        }

        try {
            HiddenApiBypass.setHiddenApiExemptions("L")
        } catch (_: Throwable) {
        }

        applySystemBarAppearance()

        setContent {
            AppTheme {
                FingerprintLauncherScreen(
                    onEnroll = ::launchFingerprintEnroll,
                    onOpenSettings = ::launchFingerprintSettings
                )
            }
        }

        mainHandler.post { maybeSetupShizukuOnFirstRun() }
    }

    override fun onResume() {
        super.onResume()
        if (isSupportedPlatform()) {
            mainHandler.post { maybeSetupShizukuOnFirstRun() }
        }
    }

    private fun isSupportedPlatform(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return false
        if (Build.VERSION.SDK_INT_FULL < MIN_ANDROID_SDK_FULL) return false
        if (!Build.MANUFACTURER.equals("samsung", ignoreCase = true)) return false

        val oneUi = getOneUiVersion()
        return oneUi >= MIN_ONE_UI_VERSION
    }

    private fun getOneUiVersion(): Int {
        return try {
            Runtime.getRuntime()
                .exec(arrayOf("getprop", "ro.build.version.oneui"))
                .inputStream
                .bufferedReader()
                .use { it.readText().trim().toIntOrNull() ?: 0 }
        } catch (_: Throwable) {
            0
        }
    }

    private fun applySystemBarAppearance(isDark: Boolean =
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    ) {
        val barColor = if (isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        window.statusBarColor = barColor
        window.navigationBarColor = barColor
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDark
        controller.isAppearanceLightNavigationBars = !isDark
    }

    private fun maybeSetupShizukuOnFirstRun() {
        if (prefs.getBoolean(KEY_SETUP_COMPLETE, false)) return
        if (prefs.getBoolean(KEY_SHIZUKU_PROMPTED, false)) return

        // Nothing to request when the secure setting permission is already present.
        if (hasWss()) {
            prefs.edit().putBoolean(KEY_SHIZUKU_PROMPTED, true)
                .putBoolean(KEY_SETUP_COMPLETE, true)
                .apply()
            return
        }

        if (!Shizuku.pingBinder()) {
            showFirstRunShizukuDialog()
            return
        }

        // This is the only automatic Shizuku permission request.
        prefs.edit().putBoolean(KEY_SHIZUKU_PROMPTED, true).apply()
        requestShizukuPermissionThen { granted ->
            if (!granted) {
                postToast("Shizuku 권한이 필요합니다.")
                return@requestShizukuPermissionThen
            }

            grantWssWithShizuku {
                if (it) {
                    prefs.edit().putBoolean(KEY_SETUP_COMPLETE, true).apply()
                    postToast("초기 설정이 완료되었습니다.")
                } else {
                    postToast("초기 설정에 실패했습니다. Shizuku 권한을 확인해주세요.")
                }
            }
        }
    }

    private fun showFirstRunShizukuDialog() {
        if (firstRunDialogShown || isFinishing) return
        firstRunDialogShown = true

        AlertDialog.Builder(this)
            .setTitle("처음 한 번만 설정해주세요")
            .setMessage(
                "지문 바로가기를 사용하려면 Shizuku 권한이 한 번 필요합니다.\n\n" +
                    "Shizuku를 실행한 뒤 이 앱으로 돌아오면 권한 요청이 자동으로 표시됩니다."
            )
            .setNegativeButton("닫기", null)
            .setPositiveButton("Shizuku 열기") { _, _ ->
                openShizukuIfInstalled()
            }
            .show()
    }

    private fun launchFingerprintEnroll(selectedId: Int) {
        if (!hasWss()) {
            Toast.makeText(
                this,
                "초기 설정이 완료되지 않았습니다. Shizuku 권한을 확인해주세요.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        launchThroughAssistant(selectedId)
    }

    private fun hasWss(): Boolean {
        return checkSelfPermission(WSS) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasShizukuPermission(): Boolean {
        if (!Shizuku.pingBinder()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private fun requestShizukuPermissionThen(result: (Boolean) -> Unit) {
        if (!Shizuku.pingBinder()) {
            result(false)
            return
        }

        try {
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != SHIZUKU_REQUEST_CODE) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    mainHandler.post {
                        result(grantResult == PackageManager.PERMISSION_GRANTED)
                    }
                }
            }

            Shizuku.addRequestPermissionResultListener(listener)
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        } catch (e: Throwable) {
            postToast("Shizuku 권한 요청 실패: ${rootCause(e).javaClass.simpleName}")
            result(false)
        }
    }

    /**
     * Uses the same package-manager operation as RootActivityLauncher, but without
     * requiring private Android SDK stubs at compile time.
     */
    private fun grantWssWithShizuku(result: (Boolean) -> Unit) {
        Thread {
            try {
                if (!Shizuku.pingBinder() || !hasShizukuPermission()) {
                    mainHandler.post { result(false) }
                    return@Thread
                }

                val packageBinder = SystemServiceHelper.getSystemService("package")
                val wrappedBinder: IBinder = ShizukuBinderWrapper(packageBinder)

                val stubClass = Class.forName("android.content.pm.IPackageManager\$Stub")
                val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
                val packageManager = asInterface.invoke(null, wrappedBinder)

                val grantMethod = stubClass.getMethod(
                    "grantRuntimePermission",
                    String::class.java,
                    String::class.java,
                    Int::class.javaPrimitiveType
                )

                grantMethod.invoke(
                    packageManager,
                    packageName,
                    WSS,
                    0
                )

                mainHandler.post {
                    result(hasWss())
                }
            } catch (e: Throwable) {
                postToast("WSS 권한 부여 실패: ${rootCause(e).javaClass.simpleName}")
                mainHandler.post { result(false) }
            }
        }.start()
    }

    /**
     * RootActivityLauncher-style assistant launch.
     * The temporary ASSISTANT value is always restored after SystemUI has consumed it.
     */
    private fun launchThroughAssistant(selectedId: Int) {
        Thread {
            val target = ComponentName(FP_PACKAGE, FP_ACTIVITY)
            val currentAssistant = Settings.Secure.getString(
                contentResolver,
                ASSISTANT_KEY
            )

            try {
                Settings.Secure.putString(
                    contentResolver,
                    ASSISTANT_KEY,
                    target.flattenToString()
                )

                val searchManager = getSystemService(SEARCH_SERVICE) as SearchManager
                HiddenApiBypass.invoke(
                    SearchManager::class.java,
                    searchManager,
                    "launchAssist",
                    Bundle().apply {
                        putInt("selected_id", selectedId)
                    }
                )

                // Give SystemUI enough time to consume the temporary assistant setting.
                Thread.sleep(RESTORE_DELAY_MS)
            } catch (e: Throwable) {
                postToast("Assistant 방식 실행 실패: ${rootCause(e).javaClass.simpleName}")
            } finally {
                try {
                    Settings.Secure.putString(
                        contentResolver,
                        ASSISTANT_KEY,
                        currentAssistant
                    )
                } catch (e: Throwable) {
                    postToast("Assistant 설정 원복 실패: ${rootCause(e).javaClass.simpleName}")
                }
            }
        }.start()
    }

    private fun openShizukuIfInstalled() {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(this, "Shizuku가 설치되어 있지 않습니다.", Toast.LENGTH_LONG).show()
            }
        } catch (_: Throwable) {
            Toast.makeText(this, "Shizuku를 열 수 없습니다.", Toast.LENGTH_LONG).show()
        }
    }

    private fun postToast(message: String) {
        mainHandler.post {
            if (!isFinishing) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun rootCause(t: Throwable): Throwable {
        var current = t
        while (current.cause != null && current.cause !== current) {
            current = current.cause!!
        }
        return current
    }

    private fun launchFingerprintSettings() {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(SETTINGS_PACKAGE, SETTINGS_ACTIVITY)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (Shizuku.pingBinder() && hasShizukuPermission()) {
            Thread {
                try {
                    val process = Shizuku.newProcess(
                        arrayOf("am", "start", "-n", "$SETTINGS_PACKAGE/$SETTINGS_ACTIVITY"),
                        null,
                        null
                    )
                    val code = process.waitFor()
                    if (code != 0) {
                        postToast("지문 인식 설정을 열 수 없습니다. (code $code)")
                    }
                } catch (e: Throwable) {
                    postToast("설정 실행 실패: ${rootCause(e).javaClass.simpleName}")
                }
            }.start()
        } else {
            startSafely(intent)
        }
    }

    private fun startSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "해당 액티비티를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "실행 권한이 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = Color(0xFF4A90FF),
            background = Color.Black,
            surface = Color(0xFF1C1C1E),
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFB9B9BE),
            outline = Color(0xFF3A3A3C)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF2F80FF),
            background = Color.White,
            surface = Color(0xFFF2F2F7),
            onBackground = Color(0xFF111111),
            onSurface = Color(0xFF111111),
            onSurfaceVariant = Color(0xFF6B6B70),
            outline = Color(0xFFD1D1D6)
        )
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun UnsupportedPlatformScreen() {
    val dark = isSystemInDarkTheme()
    val background = if (dark) Color.Black else Color.White
    val text = if (dark) Color.White else Color(0xFF111111)
    val secondary = if (dark) Color(0xFFB9B9BE) else Color(0xFF6B6B70)

    Surface(modifier = Modifier.fillMaxSize(), color = background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Fingerprint,
                contentDescription = null,
                tint = if (dark) Color.White else Color.Black,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "지원되지 않는 기기 또는 소프트웨어",
                color = text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "One UI 8.5 (Android 16.1) 이상에서만 사용할 수 있습니다.",
                color = secondary,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Samsung Galaxy 전용",
                color = secondary,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun FingerprintLauncherScreen(
    onEnroll: (Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val background = if (dark) Color.Black else Color.White
    val card = if (dark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val text = if (dark) Color.White else Color(0xFF111111)
    val icon = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val divider = if (dark) Color(0xFF2C2C2E) else Color(0xFFD1D1D6)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "지문인식 정확도 향상",
            color = text,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsCard(card) {
            SettingsRow("지문 1", { onEnroll(1) }, text, icon, secondary)
            RowDivider(divider)
            SettingsRow("지문 2", { onEnroll(2) }, text, icon, secondary)
            RowDivider(divider)
            SettingsRow("지문 3", { onEnroll(3) }, text, icon, secondary)
            RowDivider(divider)
            SettingsRow("지문 4", { onEnroll(4) }, text, icon, secondary, true)
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard(card) {
            SettingsRow(
                "지문 인식 설정 열기",
                onOpenSettings,
                text,
                icon,
                secondary,
                true
            )
        }
    }
}

@Composable
fun SettingsCard(cardColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = cardColor
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsRow(
    label: String,
    onClick: () -> Unit,
    textColor: Color,
    iconColor: Color,
    arrowColor: Color,
    isLast: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Fingerprint,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = textColor,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = arrowColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun RowDivider(dividerColor: Color) {
    HorizontalDivider(
        color = dividerColor,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 60.dp)
    )
}
