package com.surexu.sesame.ui.neo

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.surexu.sesame.R
import com.surexu.sesame.data.AppConfig
import com.surexu.sesame.util.LanguageUtil

/**
 * 设置页「系统界面」二级页：主题 / 悬浮底栏 / 界面缩放 / 触感反馈 / 日志展示 / 显示Tips。
 * 开关类偏好存 SharedPreferences("sesame_ui_state")，与主界面共享；
 * 主题与显示Tips 对接 AppConfig（与功能页基础组联动）。
 */
class NeoSystemActivity : AppCompatActivity() {

    companion object {
        const val KEY_UI_THEME = "ui_theme"
        const val KEY_UI_FLOAT_NAV = "ui_float_nav"
        const val KEY_UI_SCALE = "ui_scale"
        const val KEY_UI_HAPTIC = "ui_haptic"
        const val KEY_UI_LOG_TABS = "ui_log_tabs"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        val LOG_CATEGORIES = listOf("森林", "庄园", "其他", "记录", "错误", "调试", "运行")

        private const val PREFS_UI = "sesame_ui_state"
    }

    private lateinit var uiPrefs: SharedPreferences

    override fun attachBaseContext(newBase: Context) {
        ThemeUtil.applyNightMode()
        super.attachBaseContext(LanguageUtil.setLocal(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.neo_page_system)
        setupSystemBars()
        uiPrefs = getSharedPreferences(PREFS_UI, MODE_PRIVATE)

        findViewById<View>(R.id.neo_system_back_btn).setOnClickListener {
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

    // ==================== 行渲染 ====================

    private fun renderRows() {
        val container = findViewById<LinearLayout>(R.id.neo_system_list)
        container.removeAllViews()
        val marginPx = dp(12)

        // 主题：跟随系统 / 浅色 / 深色（对接 AppConfig.followSystem + darkMode，选择后立即生效）
        addRow(container, marginPx, "主题", themeSummary(), withSwitch = false) {
            showChoiceDialog("主题", arrayOf("跟随系统", "浅色", "深色"), themeIndex()) { index ->
                when (index) {
                    0 -> AppConfig.INSTANCE.followSystem = true
                    1 -> {
                        AppConfig.INSTANCE.followSystem = false
                        AppConfig.INSTANCE.darkMode = false
                    }
                    2 -> {
                        AppConfig.INSTANCE.followSystem = false
                        AppConfig.INSTANCE.darkMode = true
                    }
                }
                AppConfig.save()
                ThemeUtil.applyNightMode()
                recreate()
            }
        }

        // 悬浮底栏：底部导航悬浮胶囊
        addRow(
            container, marginPx, "悬浮底栏", "底部导航栏悬浮胶囊样式", withSwitch = true,
            checked = uiPrefs.getBoolean(KEY_UI_FLOAT_NAV, true),
            onChanged = { checked ->
                uiPrefs.edit().putBoolean(KEY_UI_FLOAT_NAV, checked).apply()
            }
        )

        // 界面缩放：90 / 100 / 110 / 125，重启应用生效
        addRow(container, marginPx, "界面缩放", scaleSummary(), withSwitch = false) {
            showChoiceDialog("界面缩放", arrayOf("90%", "100%", "110%", "125%"), scaleIndex()) { index ->
                uiPrefs.edit().putInt(KEY_UI_SCALE, SCALES[index]).apply()
                refreshAll()
                Toast.makeText(this, "重启应用后生效", Toast.LENGTH_SHORT).show()
            }
        }

        // 触感反馈：关键点击振动
        addRow(
            container, marginPx, "触感反馈", "点击关键按钮时振动反馈", withSwitch = true,
            checked = uiPrefs.getBoolean(KEY_UI_HAPTIC, false),
            onChanged = { checked ->
                uiPrefs.edit().putBoolean(KEY_UI_HAPTIC, checked).apply()
            }
        )

        // 日志展示：多选日志分类
        addRow(container, marginPx, "日志展示", logTabsSummary(), withSwitch = false) {
            showMultiChoiceDialog()
        }

        // 显示Tips：操作气泡提示（对接 AppConfig.showToast，与基础组「气泡提示」联动）
        addRow(
            container, marginPx, "显示Tips", "操作气泡提示（纵向偏移在基础组调整）", withSwitch = true,
            checked = AppConfig.INSTANCE.showToast ?: true,
            onChanged = { checked ->
                AppConfig.INSTANCE.showToast = checked
                AppConfig.saveAsync()
            }
        )
    }

    /** 构建一行：开关行点击整行切换开关；箭头行点击弹窗。 */
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

    // ==================== 摘要文案 ====================

    private fun themeMode(): String = when {
        themeIsLight() -> THEME_LIGHT
        AppConfig.INSTANCE.darkMode ?: false -> THEME_DARK
        else -> THEME_SYSTEM
    }

    /** 主题在选项数组中的索引：0=跟随系统，1=浅色，2=深色。 */
    private fun themeIndex(): Int = when {
        AppConfig.INSTANCE.followSystem ?: true -> 0
        AppConfig.INSTANCE.darkMode ?: false -> 2
        else -> 1
    }

    private fun themeIsLight(): Boolean = !(AppConfig.INSTANCE.followSystem ?: true) && !(AppConfig.INSTANCE.darkMode ?: false)

    private fun themeSummary(): String = when {
        themeIsLight() -> "浅色"
        AppConfig.INSTANCE.darkMode ?: false -> "深色"
        else -> "跟随系统"
    }

    private fun scaleValue(): Int = uiPrefs.getInt(KEY_UI_SCALE, 100).let { if (it in SCALES) it else 100 }

    private fun scaleIndex(): Int = SCALES.indexOf(scaleValue()).coerceAtLeast(0)

    private fun scaleSummary(): String = scaleValue().toString() + "%（重启生效）"

    private fun enabledLogTabs(): Set<String> =
        uiPrefs.getStringSet(KEY_UI_LOG_TABS, null) ?: LOG_CATEGORIES.toSet()

    private fun logTabsSummary(): String {
        val enabled = enabledLogTabs()
        return if (enabled.isEmpty()) "全部关闭" else enabled.joinToString("、")
    }

    // ==================== 弹窗 ====================

    /** 单选弹窗：title + 选项列表，点选即生效。 */
    private fun showChoiceDialog(title: String, options: Array<String>, current: Int, onPick: (Int) -> Unit) {
        if (options.isEmpty()) {
            Toast.makeText(this, "无可用选项", Toast.LENGTH_SHORT).show()
            return
        }
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.neo_dialog_field_edit)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(true)
        val width = resources.displayMetrics.widthPixels - dp(64)
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.findViewById<TextView>(R.id.neo_edit_title).text = title
        dialog.findViewById<TextView>(R.id.neo_edit_ok).visibility = View.GONE
        dialog.findViewById<TextView>(R.id.neo_edit_cancel).setOnClickListener { dialog.dismiss() }

        val content = dialog.findViewById<FrameLayout>(R.id.neo_edit_content)
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        options.forEachIndexed { index, opt ->
            val optView = TextView(this).apply {
                text = opt
                setTextColor(
                    ContextCompat.getColor(
                        this@NeoSystemActivity,
                        if (index == current) R.color.neo_primary else R.color.neo_text_primary
                    )
                )
                textSize = 15f
                setTypeface(typeface, if (index == current) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                setPadding(dp(6), dp(10), dp(6), dp(10))
                background = ContextCompat.getDrawable(this@NeoSystemActivity, R.drawable.neu_input_bg)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 0, dp(8))
                layoutParams = lp
                setOnClickListener {
                    onPick(index)
                    dialog.dismiss()
                }
            }
            inner.addView(optView)
        }
        content.addView(
            inner,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        dialog.show()
    }

    /** 日志展示多选弹窗：勾选需要展示的分类，确定后保存。 */
    private fun showMultiChoiceDialog() {
        val selected = enabledLogTabs().toMutableSet()
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.neo_dialog_field_edit)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(true)
        val width = resources.displayMetrics.widthPixels - dp(64)
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.findViewById<TextView>(R.id.neo_edit_title).text = "日志展示"
        dialog.findViewById<TextView>(R.id.neo_edit_cancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<TextView>(R.id.neo_edit_ok).setOnClickListener {
            uiPrefs.edit().putStringSet(KEY_UI_LOG_TABS, selected).apply()
            refreshAll()
            dialog.dismiss()
        }

        val content = dialog.findViewById<FrameLayout>(R.id.neo_edit_content)
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        LOG_CATEGORIES.forEach { category ->
            val optView = TextView(this).apply {
                text = if (selected.contains(category)) "✓ $category" else category
                setTextColor(
                    ContextCompat.getColor(
                        this@NeoSystemActivity,
                        if (selected.contains(category)) R.color.neo_primary else R.color.neo_text_primary
                    )
                )
                textSize = 15f
                setTypeface(typeface, if (selected.contains(category)) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                setPadding(dp(6), dp(10), dp(6), dp(10))
                background = ContextCompat.getDrawable(this@NeoSystemActivity, R.drawable.neu_input_bg)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 0, dp(8))
                layoutParams = lp
                setOnClickListener {
                    if (selected.contains(category)) {
                        selected.remove(category)
                    } else {
                        selected.add(category)
                    }
                    text = if (selected.contains(category)) "✓ $category" else category
                    setTextColor(
                        ContextCompat.getColor(
                            this@NeoSystemActivity,
                            if (selected.contains(category)) R.color.neo_primary else R.color.neo_text_primary
                        )
                    )
                    setTypeface(typeface, if (selected.contains(category)) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                }
            }
            inner.addView(optView)
        }
        content.addView(
            inner,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        dialog.show()
    }

    // ==================== 工具 ====================

    private fun refreshAll() {
        val container = findViewById<LinearLayout>(R.id.neo_system_list)
        container.removeAllViews()
        renderRows()
    }

    /** 触感反馈：开关开启时振动（优先 Vibrator 直振，失败回退系统反馈）。 */
    private fun haptic(view: View) {
        if (!uiPrefs.getBoolean(KEY_UI_HAPTIC, false)) return
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

    private val SCALES = listOf(90, 100, 110, 125)
}
