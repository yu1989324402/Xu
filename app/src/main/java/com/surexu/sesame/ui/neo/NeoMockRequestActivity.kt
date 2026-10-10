package com.surexu.sesame.ui.neo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.surexu.sesame.R
import com.surexu.sesame.util.LanguageUtil

/**
 * 「服务」→「模拟请求」独立页：输入 mtop 接口方法/数据，发送真实请求，
 * 结果直接展示在本页（不再弹窗）。发送走 sesame.rpctest 广播通道，
 * 支付宝侧 ExtensionsHandle.mockRequest 执行并广播回传。
 */
class NeoMockRequestActivity : AppCompatActivity() {

    private val mockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_MOCK_RESULT) return
            val method = intent.getStringExtra("method").orEmpty()
            val result = intent.getStringExtra("result").orEmpty()
            findViewById<TextView>(R.id.neo_mock_result_content).text =
                buildString {
                    if (method.isNotEmpty()) append("方法：").append(method).append("\n\n")
                    append(if (result.isEmpty()) "无返回内容" else result)
                }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        ThemeUtil.applyNightMode()
        super.attachBaseContext(LanguageUtil.setLocal(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.neo_page_mock_request)
        setupSystemBars()

        findViewById<View>(R.id.neo_mock_back_btn).setOnClickListener {
            haptic(it)
            finish()
        }

        registerMockResultReceiver()

        findViewById<View>(R.id.neo_mock_send_btn).setOnClickListener {
            haptic(it)
            sendMockRequest()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(mockReceiver)
        } catch (e: Exception) {
            // 未注册或已注销
        }
    }

    private fun setupSystemBars() {
        window.statusBarColor = getColor(R.color.neo_base)
        window.navigationBarColor = getColor(R.color.neo_base)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !ThemeUtil.isNightActive(window.decorView.context)
            isAppearanceLightNavigationBars = !ThemeUtil.isNightActive(window.decorView.context)
        }
    }

    private fun registerMockResultReceiver() {
        val filter = IntentFilter(ACTION_MOCK_RESULT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mockReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(mockReceiver, filter)
        }
    }

    private fun sendMockRequest() {
        val api = findViewById<EditText>(R.id.neo_mock_api_input).text?.toString()?.trim().orEmpty()
        val data = findViewById<EditText>(R.id.neo_mock_data_input).text?.toString()?.trim().orEmpty()
        if (api.isEmpty()) {
            Toast.makeText(this, "请输入接口 api", Toast.LENGTH_SHORT).show()
            return
        }
        findViewById<TextView>(R.id.neo_mock_result_content).text = "请求发送中…"
        val intent = Intent("com.eg.android.AlipayGphone.sesame.rpctest")
        intent.putExtra("type", "mockRequest")
        intent.putExtra("method", api)
        intent.putExtra("data", data)
        sendBroadcast(intent)
        Toast.makeText(this, "模拟请求已发送，结果稍后展示，也可在日志页查看", Toast.LENGTH_SHORT).show()
    }

    private val uiPrefs by lazy { getSharedPreferences("sesame_ui_state", MODE_PRIVATE) }

    /** 触感反馈：开关开启时振动（优先 Vibrator 直振，失败回退系统反馈）。 */
    private fun haptic(view: View) {
        if (!uiPrefs.getBoolean(NeoSystemActivity.KEY_UI_HAPTIC, false)) return
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(25, 160))
                } else {
                    vibrator.vibrate(25)
                }
                return
            }
        } catch (_: Exception) {
            // 无 VIBRATE 权限等异常时回退系统反馈
        }
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private companion object {
        const val ACTION_MOCK_RESULT = "com.surexu.sesame.mockRequestResult"
    }
}
