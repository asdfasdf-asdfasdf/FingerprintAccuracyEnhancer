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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class MainActivity : ComponentActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private var firstRunDialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // RootActivityLauncher uses the same library so hidden SearchManager APIs can be used.
        try {
            HiddenApiBypass.setHiddenApiExemptions("L")
        } catch (_: Throwable) {
        }

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
        // If Shizuku was started from the first-run dialog, retry the one automatic request.
        mainHandler.post { maybeSetupShizukuOnFirstRun() }
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
        startSafely(intent)
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
    val colorScheme = darkColorScheme(
        primary = Color(0xFF4A90FF),
        background = Color.Black,
        surface = Color(0xFF1C1C1E)
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}

@Composable
fun FingerprintLauncherScreen(
    onEnroll: (Int) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "지문인식 정확도 향상",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        SettingsCard {
            SettingsRow(label = "지문 1", onClick = { onEnroll(1) })
            RowDivider()
            SettingsRow(label = "지문 2", onClick = { onEnroll(2) })
            RowDivider()
            SettingsRow(label = "지문 3", onClick = { onEnroll(3) })
            RowDivider()
            SettingsRow(label = "지문 4", onClick = { onEnroll(4) }, isLast = true)
        }

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard {
            SettingsRow(
                label = "지문 인식 설정 열기",
                onClick = onOpenSettings,
                isLast = true
            )
        }
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1C1C1E)
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsRow(label: String, onClick: () -> Unit, isLast: Boolean = false) {
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
            tint = Color(0xFF4A90FF),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color(0xFF6B6B6E),
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(
        color = Color(0xFF2C2C2E),
        thickness = 1.dp,
        modifier = Modifier.padding(start = 60.dp)
    )
}
