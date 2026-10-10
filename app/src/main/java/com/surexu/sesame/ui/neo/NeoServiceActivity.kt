package com.surexu.sesame.ui.neo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.surexu.sesame.R
import com.surexu.sesame.data.AppConfig
import com.surexu.sesame.util.LanguageUtil

/**
 * 设置页「服务」二级页：好友统计 / 扩展功能 / 隐藏图标 / 为支付宝申请后台运行权限。
 * 前两项跳子二级页，后两项为本地开关（图标 alias 切换 + 电池优化白名单引导）。
 */
class NeoServiceActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        ThemeUtil.applyNightMode()
        super.attachBaseContext(LanguageUtil.setLocal(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.neo_page_service)
        setupSystemBars()

        findViewById<View>(R.id.neo_service_back_btn).setOnClickListener {
            haptic(it)
            finish()
        }

        renderRows()
    }

    private fun setupSystemBars() {
        window.statusBarColor = getColor(R.color.neo_base)
        window.navigationBarColor = getColor(R.color.neo_base)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !ThemeUtil.isNightActive(window.decorView.context)
            isAppearanceLightNavigationBars = !ThemeUtil.isNightActive(window.decorView.context)
        }
    }

    private fun renderRows() {
        val container = findViewById<LinearLayout>(R.id.neo_service_list)
        container.removeAllViews()
        val marginPx = dp(12)

        // 模拟请求：独立页输入 mtop 接口方法/数据，发送真实请求并展示结果
        addRow(container, marginPx, "模拟请求", "向支付宝 mtop 接口发送一次真实请求", withSwitch = false) {
            startActivity(Intent(this, NeoMockRequestActivity::class.java))
        }

        // 好友统计：展示单向好友列表
        addRow(container, marginPx, "好友统计", "查看单向好友与能量统计", withSwitch = false) {
            startActivity(Intent(this, NeoFriendStatsActivity::class.java))
        }

        // 单删好友：勾选单向好友并删除（删除走广播到支付宝进程执行 RPC）
        addRow(container, marginPx, "单删好友", "检测并删除单向好友（对方删了你的）", withSwitch = false) {
            startActivity(Intent(this, NeoFriendManageActivity::class.java))
        }

        // 扩展功能：森林查询 / 自定义走路路径 / 自动切号等
        addRow(container, marginPx, "扩展功能", "森林查询、走路路径、自动切号等", withSwitch = false) {
            startActivity(Intent(this, NeoExtensionsActivity::class.java))
        }

        // 隐藏图标：切换桌面启动图标（MainActivityAlias 组件）
        addRow(
            container, marginPx, "隐藏图标", "隐藏桌面图标后通过其他入口进入", withSwitch = true,
            checked = isIconHidden(),
            onChanged = { checked ->
                toggleHideIcon()
                Toast.makeText(this, if (checked) "图标已隐藏" else "图标已恢复", Toast.LENGTH_SHORT).show()
            }
        )

        // 为支付宝申请后台运行权限：开关 + 未授权时引导立即申请
        addRow(
            container, marginPx, "为支付宝申请后台权限", "让支付宝保持后台运行不被省电清理", withSwitch = true,
            checked = AppConfig.INSTANCE.batteryPerm ?: true,
            onChanged = { checked ->
                AppConfig.INSTANCE.batteryPerm = checked
                AppConfig.saveAsync()
                refreshAll()
            }
        )
        if (AppConfig.INSTANCE.batteryPerm ?: true && !alipayBatteryIgnored()) {
            addRow(container, marginPx, "立即申请权限", "跳转系统电池优化设置", withSwitch = false) {
                requestAlipayBatteryPerm()
            }
        }
    }

    // ==================== 隐藏图标 ====================

    private fun isIconHidden(): Boolean {
        val alias = ComponentName(this, ALIAS_NAME)
        return packageManager.getComponentEnabledSetting(alias) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    private fun toggleHideIcon() {
        val alias = ComponentName(this, ALIAS_NAME)
        val state = packageManager.getComponentEnabledSetting(alias)
        val newState = if (state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }
        packageManager.setComponentEnabledSetting(alias, newState, PackageManager.DONT_KILL_APP)
    }

    // ==================== 支付宝后台权限 ====================

    private fun alipayBatteryIgnored(): Boolean {
        return try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(ALIPAY_PACKAGE) == true
        } catch (e: Exception) {
            false
        }
    }

    private fun requestAlipayBatteryPerm() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "申请权限失败", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== 行渲染 ====================

    private fun addRow(
        container: LinearLayout,
        marginPx: Int,
        name: String,
        sub: String,
        withSwitch: Boolean,
        checked: Boolean = false,
        onChanged: (Boolean) -> Unit = {},
        onTap: () -> Unit = {},
    ) {
        val row = layoutInflater.inflate(R.layout.neo_item_system_row, container, false)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 0, 0, marginPx)
        row.layoutParams = lp

        row.findViewById<TextView>(R.id.neo_system_row_name).text = name
        row.findViewById<TextView>(R.id.neo_system_row_sub).text = sub

        val switch = row.findViewById<Switch>(R.id.neo_system_row_switch)
        val arrow = row.findViewById<TextView>(R.id.neo_system_row_arrow)
        if (withSwitch) {
            switch.visibility = View.VISIBLE
            switch.isChecked = checked
            switch.setOnCheckedChangeListener { _, isChecked ->
                haptic(row)
                onChanged(isChecked)
            }
            row.setOnClickListener {
                switch.isChecked = !switch.isChecked
            }
        } else {
            arrow.visibility = View.VISIBLE
            row.setOnClickListener {
                haptic(row)
                onTap()
            }
        }
        container.addView(row)
    }

    private fun refreshAll() {
        val container = findViewById<LinearLayout>(R.id.neo_service_list)
        container.removeAllViews()
        renderRows()
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val ALIAS_NAME = "com.surexu.sesame.ui.MainActivityAlias"
        const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"
    }
}
