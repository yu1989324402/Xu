package com.surexu.sesame.model.base;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.surexu.sesame.hook.ApplicationHook;
import com.surexu.sesame.util.Log;
import com.surexu.sesame.util.MessageUtil;
import com.surexu.sesame.util.TimeUtil;

/**
 * 完成任务的另一种实现方案：完成接口被 400000040「不支持rpc调用」拒绝时，换
 * {@code com.alipay.antfarm.doFarmTask} 再试。
 *
 * <p>该接口的响应不可信（常回 102「服务器正在开小差」而任务其实已生效），所以只发不判，
 * 成败一律以任务列表为准（{@link #verify} 或下一轮列表）。
 */
public final class TaskAlternative {

    /** 庄园路径验证过的 version；服务端不校验 version，各模块可沿用自己那份。 */
    public static final String DEFAULT_VERSION = "1.8.2302070202.46";

    /**
     * 交易/履约类任务关键词：**只认 bizKey**（服务端稳定字段），这类任务只能靠真实交易完成，
     * 用 doFarmTask 伪申报会被判风险操作（服务端回 1009 风控），一律不发，交自动黑名单。
     *
     * <p>关键词逐条来自实际日志里出现过的 bizKey：
     * <pre>
     * OFFLINE_PAY / ONLINE_PAY / MYZY_pay_* / ORCHARD_NORMAL_XIANXIAZHIFU100  → 支付
     * LSHS_xiadan_202509 / SHANGOU_xiadan / HANGOU_xiadan                   → 下单（回收/闪购）
     * ORCHARD_NCLY_CHARGE1_XDDQ / ORCHARD_NORMAL_CHONGZHI9                  → 充值
     * GOLDENBEAN_GAME_CZ_XDDQ_AI / GROUP_DAILY_GAME_CZ / 2026cc_cz6ylyb_fz  → 游戏充值
     * ORCHARD_NORMAL_KUAIDI100 / ORCHARD_NORMAL_JIUYIHUISHOU_VISIT          → 寄件/回收
     * </pre>
     *
     * <p><b>不要再加 {@code taobao} 这类宽泛词、也不要匹配任务标题</b>：标题/宽泛词会把外跳类
     * 任务误伤成交易类而拒绝申报（如 {@code ORCHARD_NORMAL_TAOBAOTAOLIPAI_VISIT} 曾被 taobao
     * 误拦，随后又被自动拉黑）——外跳类本应去尝试。
     *
     * <p><b>匹配方式按词的歧义度分三档，不能一律用 {@code contains}</b>：短词/缩写（{@code pay}、
     * {@code charge}、{@code _cz}）作为自由子串会命中任意夹带该串的 bizKey——实测良民任务
     * 「逛羊舍喂小羊」等即因此被误判为交易类而永久拉黑。
     * <ul>
     *   <li>{@link #LONG_KEYWORDS}：长拼音词（&ge;6 字母），夹带概率极低，用子串匹配；</li>
     *   <li>{@link #SHORT_KEYWORDS}：英文短词（&le;5 字母），要求两侧不得紧邻字母；</li>
     *   <li>{@code cz} 特判：仅认 {@code game_cz}，或 {@code _cz} 后跟数字（充值 N 元，如
     *       {@code 2026cc_cz6ylyb_fz}）。裸 {@code _cz} 结尾 / 被字母夹带的 {@code cz} 一律不认。</li>
     * </ul>
     */
    private static final String[] LONG_KEYWORDS = {
            // 支付 / 下单 / 缴费 / 充值（拼音连写，长度足够区分）
            "xiadan", "zhifu", "goumai", "jiaofei", "huankuan", "chongzhi",
            // 履约：寄件 / 回收
            "kuaidi", "huishou",
            // 酒店
            "jiudian",
    };

    /** 英文短词：两侧不得紧邻字母（数字/下划线/连字符/首尾都算边界）。 */
    private static final String[] SHORT_KEYWORDS = {
            "pay", "charge",
            // 租赁 / 出行 / 机票 / 余额宝
            "zulin", "zuche", "dache", "jipiao", "yuebao",
    };

    /** {@code _cz} 后跟数字（充值 N 元）的判据，如 {@code 2026cc_cz6ylyb_fz}。 */
    private static final Pattern CZ_AMOUNT = Pattern.compile("_cz[0-9]");

    /** bizKey 命中交易/履约类关键词，即视为交易/履约类。 */
    public static boolean isTransactionTask(String bizKey) {
        if (bizKey == null || bizKey.isEmpty()) {
            return false;
        }
        String key = bizKey.toLowerCase();
        for (String keyword : LONG_KEYWORDS) {
            if (key.contains(keyword)) {
                return true;
            }
        }
        for (String keyword : SHORT_KEYWORDS) {
            // 两侧不得紧邻字母：避免 pay/charge/dache 等短词被任意编码串夹带误命中
            if (Pattern.compile("(?<![a-z])" + keyword + "(?![a-z])").matcher(key).find()) {
                return true;
            }
        }
        // 游戏内充值：game_cz 明确，或 _cz 后跟数字（裸 _cz 结尾不算，避免误伤含 cz 的普通任务）
        return key.contains("game_cz") || CZ_AMOUNT.matcher(key).find();
    }

    /** 日志出口（{@code Log.farm/forest/other/goldenBeans}）。 */
    public interface LogSink {
        void log(String message);
    }

    private TaskAlternative() {
    }

    /** 唯一的 doFarmTask payload，返回原始响应。 */
    public static String request(String bizKey, String taskSceneCode, String version) {
        if (isTransactionTask(bizKey)) {
            Log.i("⏭️跳过交易/履约类任务#bizKey=" + bizKey + "，不自动申报");
            return "{}";
        }
        String args = "[{\"bizKey\":\"" + bizKey + "\",\"requestType\":\"RPC\",\"sceneCode\":\"ANTFARM\","
                + "\"source\":\"H5\",\"taskSceneCode\":\"" + taskSceneCode + "\",\"version\":\"" + version + "\"}]";
        return ApplicationHook.requestString("com.alipay.antfarm.doFarmTask", args);
    }

    public static JSONObject doFarmTask(String bizKey, String taskSceneCode, String version) throws JSONException {
        return new JSONObject(request(bizKey, taskSceneCode, version));
    }

    /** 日志片段 {@code resultCode/memo}（{@code desc}、{@code resultDesc} 兜底）。 */
    public static String describe(JSONObject jo) {
        if (jo == null) {
            return "无响应";
        }
        String code = jo.optString("resultCode", jo.optString("code", ""));
        String memo = jo.optString("memo", jo.optString("desc", jo.optString("resultDesc", "")));
        if (code.isEmpty() && memo.isEmpty()) {
            return "无响应";
        }
        return code + "/" + memo;
    }

    /** 是否该换另一种实现方案：400000040 且 taskSceneCode 非空。 */
    public static boolean hit(JSONObject failJo, String taskSceneCode) {
        return taskSceneCode != null && !taskSceneCode.trim().isEmpty()
                && MessageUtil.isUnsupportedRpc(failJo);
    }

    /** version 取 {@link #DEFAULT_VERSION}。 */
    public static JSONObject trigger(Map<String, String> pending, String taskId, String taskTitle,
                                     String bizKey, String taskSceneCode, String logPrefix, LogSink sink) {
        return trigger(pending, taskId, taskTitle, bizKey, taskSceneCode, DEFAULT_VERSION, logPrefix, sink);
    }

    /**
     * 发请求 + 打日志 + 登记 {@code taskId -> taskTitle}（{@code pending} 可为 null）；不做成败判定。
     *
     * @return doFarmTask 的响应；调用异常返回 null（按未触发处理）
     */
    public static JSONObject trigger(Map<String, String> pending, String taskId, String taskTitle,
                                     String bizKey, String taskSceneCode, String version,
                                     String logPrefix, LogSink sink) {
        try {
            if (isTransactionTask(bizKey)) {
                Log.i(logPrefix + "⏭️跳过[" + taskTitle + "]#bizKey=" + bizKey + "，交易/履约类不自动申报");
                // 跳过 = 该任务不会完成：同样登记进同轮核对，由调用方的核对机制把它拉黑。
                // 否则只跳过、不拉黑，任务每天被反复跳过（会员游戏中心/金豆夺宝走的就是这条路）。
                if (pending != null && taskId != null && !taskId.isEmpty()) {
                    pending.put(taskId, taskTitle);
                }
                return null;
            }
            JSONObject doFarmJo = doFarmTask(bizKey, taskSceneCode, version);
            if (pending != null && taskId != null && !taskId.isEmpty()) {
                pending.put(taskId, taskTitle);
            }
            String message = logPrefix + "🕓已触发[" + taskTitle + "]#响应=" + describe(doFarmJo)
                    + "，结果以任务列表为准";
            if (sink != null) {
                sink.log(message);
            } else {
                Log.other(message);
            }
            return doFarmJo;
        } catch (Throwable t) {
            Log.err("TaskAlternative", "trigger err:", t);
            return null;
        }
    }

    // ==================== 同轮核对 ====================

    /** 同轮核对配置。{@code blacklistByTitle} true=拉黑写任务标题，false=写 taskId。 */
    public static final class VerifyConfig {
        private final String moduleName;
        private final String taskListField;
        private final String listDisplay;
        private final String logPrefix;
        private final String doneTag;
        private final boolean blacklistByTitle;
        private final LogSink sink;

        public VerifyConfig(String moduleName, String taskListField, String listDisplay,
                            String logPrefix, String doneTag, boolean blacklistByTitle, LogSink sink) {
            this.moduleName = moduleName;
            this.taskListField = taskListField;
            this.listDisplay = listDisplay;
            this.logPrefix = logPrefix;
            this.doneTag = doneTag;
            this.blacklistByTitle = blacklistByTitle;
            this.sink = sink;
        }
    }

    /** 重拉任务列表并返回仍未完成的 id；拉取失败返回 null。 */
    public interface TaskListSnapshot {
        Set<String> stillTodo() throws Exception;
    }

    /**
     * 同轮核对：等 3 秒 → 重拉列表 → 仍未完成才拉黑，否则记「完成（已按任务列表核对）」。
     *
     * @return 是否有任务确认完成
     */
    public static boolean verify(Map<String, String> pending, VerifyConfig cfg, TaskListSnapshot snapshot) {
        if (pending == null || pending.isEmpty()) {
            return false;
        }
        Map<String, String> batch = new LinkedHashMap<>(pending);
        pending.clear();
        try {
            TimeUtil.sleep(3000);
            Set<String> stillTodo = snapshot.stillTodo();
            if (stillTodo == null) {
                return false;
            }
            boolean changed = false;
            for (Map.Entry<String, String> item : batch.entrySet()) {
                String key = item.getKey();
                String title = item.getValue();
                if (stillTodo.contains(key)) {
                    cfg.sink.log(cfg.logPrefix + "⚠️未完成[" + title + "]，已交给自动拉黑机制");
                    MessageUtil.MarkTaskBlackList(cfg.moduleName, cfg.taskListField, cfg.listDisplay,
                            cfg.blacklistByTitle ? title : key);
                } else {
                    cfg.sink.log(cfg.logPrefix + cfg.doneTag + "[" + title + "]（已按任务列表核对）");
                    changed = true;
                }
            }
            return changed;
        } catch (Throwable t) {
            Log.err("TaskAlternative", "verify err:", t);
            return false;
        }
    }
}
