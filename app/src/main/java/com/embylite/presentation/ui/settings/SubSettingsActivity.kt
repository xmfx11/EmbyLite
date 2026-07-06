package com.embylite.presentation.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.embylite.presentation.theme.EmbyLiteTheme
import com.embylite.presentation.ui.update.UpdateScreen

/**
 * 登录页设置菜单入口的容器 Activity
 * type: "update" | "logs"
 * 登录前也可访问（不依赖 TokenManager）
 */
class SubSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = intent.getStringExtra("type") ?: "update"
        try {
            setContent {
                EmbyLiteTheme {
                    when (type) {
                        "update" -> UpdateScreen(onBack = { finish() })
                        "logs" -> LogViewerScreen(onBack = { finish() })
                        else -> UpdateScreen(onBack = { finish() })
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SubSettingsActivity", "setContent crash", e)
            finish()
        }
    }
}
