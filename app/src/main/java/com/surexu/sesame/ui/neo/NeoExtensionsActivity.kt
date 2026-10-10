package com.surexu.sesame.ui.neo

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.surexu.sesame.R
import com.surexu.sesame.data.TokenConfig
import com.surexu.sesame.util.FileUtil
import com.surexu.sesame.util.LanguageUtil
import java.io.File

/**
 * 「服务」→「扩展功能」二级页：森林查询 / 自定义走路路径 / 自动切号 / 清空光盘行动图片。
 * 与旧版模块 UI 的扩展功能页功能对齐：查询类通过广播向支付宝模块发 RPC 请求。
 */
class NeoExtensionsActivity : AppCompatActivity() {

    private var intervalSeconds = 7200
    private var switchEnabled = false
    private var activation = 0L

    override fun attachBaseContext(newBase: Context) {
        ThemeUtil.applyNightMode()
        super.attachBaseContext(LanguageUtil.setLocal(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.neo_page_extensions)
        setupSystemBars()

        findViewById<View>(R.id.neo_extensions_back_btn).setOnClickListener {
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
        val container = findViewById<LinearLayout>(R.id.neo_extensions_list)
        container.removeAllViews()
        val marginPx = dp(12)

        // ==================== 森林查询 ====================
        addSectionTitle(container, "森林查询")
        val forestButtons = listOf(
            "查询浇水列表" to ("antForest" to "getWateredItems"),
            "查询被浇列表" to ("antForest" to "getWateringItems"),
            "查询古树列表" to ("antForest" to "getTreeItems"),
            "查询新古树" to ("antForest" to "getNewTreeItems"),
            "查询区域古树" to ("antForest" to "queryAreaTrees"),
            "查询可解锁古树" to ("antForest" to "getUnlockTreeItems"),
            "填入浇水好友" to ("antForest" to "fillWateredFriendList")
        )
        forestButtons.forEach { (label, pair) ->
            addArrowRow(container, marginPx, label, null) {
                sendItemsBroadcast(pair.first, pair.second, null)
                Toast.makeText(this, "已发送查询请求，请在森林日志查看结果！", Toast.LENGTH_SHORT).show()
            }
        }

        // ==================== 自定义走路路径 ====================
        addSectionTitle(container, "自定义走路路径")
        addArrowRow(container, marginPx, "设置自定义走路路径(list)", null) {
            showPathInputDialog("list")
        }
        addArrowRow(container, marginPx, "设置自定义走路路径(queue)", null) {
            showPathInputDialog("queue")
        }

        // ==================== 自动切号 ====================
        addSectionTitle(container, "自动切号")
        readAccountSwitchSettings().let { (enabled, seconds, act) ->
            switchEnabled = enabled
            intervalSeconds = seconds
            activation = act
        }
        addSwitchRow(container, marginPx, "启用自动切号", "按间隔自动轮换登录的支付宝账号", switchEnabled) { on ->
            if (writeAccountSwitchSettings(on, intervalSeconds, activation)) {
                switchEnabled = on
                Toast.makeText(this, if (on) "自动切号已开启" else "自动切号已关闭", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
            }
        }
        addArrowRow(container, marginPx, "切换间隔", "每 ${intervalSeconds / 60} 分钟切换一次") {
            showIntervalDialog()
        }

        // ==================== 其他 ====================
        addSectionTitle(container, "其他")
        addArrowRow(container, marginPx, "清空光盘行动图片", null) {
            showClearDishConfirm()
        }
    }

    // ==================== 森林查询 / 走路路径广播 ====================

    fun sendItemsBroadcast(type: String, method: String, data: String?) {
        val intent = Intent("com.eg.android.AlipayGphone.sesame.rpctest")
        intent.putExtra("type", type)
        intent.putExtra("method", method)
        intent.putExtra("data", data)
        sendBroadcast(intent)
    }

    // ==================== 自动切号配置（独立于 ConfigV2 的 account_switch_settings.json）====================

    private val accountSwitchFile: File
        get() = File(FileUtil.MAIN_DIRECTORY_FILE, "account_switch_settings.json")

    private fun readAccountSwitchSettings(): Triple<Boolean, Int, Long> {
        return try {
            val file = accountSwitchFile
            if (file.isFile && file.length() <= 16384) {
                val text = file.readText()
                val json = if (text.isEmpty()) org.json.JSONObject() else org.json.JSONObject(text)
                val enabled = json.optBoolean("enabled", false)
                val seconds = json.optInt("intervalSeconds", 7200).coerceIn(15, 86400)
                val activation = json.optLong("activation", 0L)
                Triple(enabled, seconds, activation)
            } else {
                Triple(false, 7200, 0L)
            }
        } catch (e: Exception) {
            Triple(false, 7200, 0L)
        }
    }

    private fun writeAccountSwitchSettings(enabled: Boolean, seconds: Int, activation: Long): Boolean {
        return try {
            val json = org.json.JSONObject()
            json.put("enabled", enabled)
            json.put("intervalSeconds", seconds.coerceIn(15, 86400))
            json.put("activation", activation)
            accountSwitchFile.writeText(json.toString())
            true
        } catch (e: Exception) {
            false
        }
    }

    // ==================== 弹窗 ====================

    private fun showPathInputDialog(mode: String) {
        val dialog = newDialog()
        dialog.findViewById<TextView>(R.id.neo_edit_title).text =
            if (mode == "list") "设置自定义走路路径(list)" else "设置自定义走路路径(queue)"
        val content = dialog.findViewById<FrameLayout>(R.id.neo_edit_content)
        val editText = EditText(this).apply {
            hint = "路径ID"
            textSize = 15f
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(getColor(R.color.neo_text_primary))
            setHintTextColor(getColor(R.color.neo_text_hint))
            background = ContextCompat.getDrawable(this@NeoExtensionsActivity, R.drawable.neu_input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        content.addView(
            editText,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        dialog.findViewById<TextView>(R.id.neo_edit_cancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<TextView>(R.id.neo_edit_ok).setOnClickListener {
            val text = editText.text?.toString()?.trim().orEmpty()
            if (mode == "list") {
                sendItemsBroadcast("setCustomWalkPathIdList", "addCustomWalkPathId", text)
            } else {
                sendItemsBroadcast("setCustomWalkPathIdQueue", "addCustomWalkPathIdQueue", text)
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showIntervalDialog() {
        val dialog = newDialog()
        dialog.findViewById<TextView>(R.id.neo_edit_title).text = "切换间隔（分钟）"
        val content = dialog.findViewById<FrameLayout>(R.id.neo_edit_content)
        val editText = EditText(this).apply {
            hint = "1~1440 分钟"
            textSize = 15f
            inputType = InputType.TYPE_CLASS_NUMBER
            setText((intervalSeconds / 60).toString())
            setTextColor(getColor(R.color.neo_text_primary))
            setHintTextColor(getColor(R.color.neo_text_hint))
            background = ContextCompat.getDrawable(this@NeoExtensionsActivity, R.drawable.neu_input_bg)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        content.addView(
            editText,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        dialog.findViewById<TextView>(R.id.neo_edit_cancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<TextView>(R.id.neo_edit_ok).setOnClickListener {
            val minutes = editText.text?.toString()?.trim()?.toIntOrNull()
            if (minutes == null || minutes < 1 || minutes > 1440) {
                Toast.makeText(this, "请输入 1~1440 分钟的整数", Toast.LENGTH_SHORT).show()
            } else {
                val newSeconds = (minutes * 60).coerceIn(15, 86400)
                if (writeAccountSwitchSettings(switchEnabled, newSeconds, activation)) {
                    intervalSeconds = newSeconds
                    dialog.dismiss()
                    Toast.makeText(this, "间隔已更新", Toast.LENGTH_SHORT).show()
                    refreshAll()
                } else {
                    Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }

    private fun showClearDishConfirm() {
        val dialog = newDialog()
        dialog.findViewById<TextView>(R.id.neo_edit_title).text = "清空光盘行动图片"
        val content = dialog.findViewById<FrameLayout>(R.id.neo_edit_content)
        val tip = TextView(this).apply {
            text = "确认清空 ${TokenConfig.getDishImageCount()} 组光盘行动图片？"
            textSize = 14f
            setTextColor(getColor(R.color.neo_text_primary))
        }
        content.addView(
            tip,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        dialog.findViewById<TextView>(R.id.neo_edit_cancel).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<TextView>(R.id.neo_edit_ok).setOnClickListener {
            dialog.dismiss()
            if (TokenConfig.clearDishImage()) {
                Toast.makeText(this, "光盘行动图片清空成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "光盘行动图片清空失败", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun newDialog(): Dialog {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.neo_dialog_field_edit)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setCanceledOnTouchOutside(true)
        val width = resources.displayMetrics.widthPixels - dp(64)
        dialog.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        return dialog
    }

    // ==================== 行渲染 ====================

    private fun addSectionTitle(container: LinearLayout, title: String) {
        val tv = TextView(this).apply {
            text = title
            textSize = 13f
            setTextColor(getColor(R.color.neo_text_hint))
            setPadding(0, dp(12), 0, dp(6))
        }
        container.addView(tv)
    }

    private fun addArrowRow(
        container: LinearLayout,
        marginPx: Int,
        name: String,
        sub: String?,
        onTap: () -> Unit,
    ) {
        val row = layoutInflater.inflate(R.layout.neo_item_system_row, container, false)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 0, 0, marginPx)
        row.layoutParams = lp

        row.findViewById<TextView>(R.id.neo_system_row_name).text = name
        val subView = row.findViewById<TextView>(R.id.neo_system_row_sub)
        if (sub.isNullOrEmpty()) {
            subView.visibility = View.GONE
        } else {
            subView.text = sub
            subView.visibility = View.VISIBLE
        }
        row.findViewById<TextView>(R.id.neo_system_row_arrow).visibility = View.VISIBLE
        row.setOnClickListener {
            haptic(row)
            onTap()
        }
        container.addView(row)
    }

    private fun addSwitchRow(
        container: LinearLayout,
        marginPx: Int,
        name: String,
        sub: String,
        checked: Boolean,
        onChanged: (Boolean) -> Unit,
    ) {
        val row = layoutInflater.inflate(R.layout.neo_item_system_row, container, false)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.setMargins(0, 0, 0, marginPx)
        row.layoutParams = lp

        row.findViewById<TextView>(R.id.neo_system_row_name).text = name
        row.findViewById<TextView>(R.id.neo_system_row_sub).text = sub
        val switch = row.findViewById<Switch>(R.id.neo_system_row_switch)
        switch.visibility = View.VISIBLE
        switch.isChecked = checked
        switch.setOnCheckedChangeListener { _, isChecked ->
            haptic(row)
            onChanged(isChecked)
        }
        row.setOnClickListener {
            switch.isChecked = !switch.isChecked
        }
        container.addView(row)
    }

    private fun refreshAll() {
        val container = findViewById<LinearLayout>(R.id.neo_extensions_list)
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
}
