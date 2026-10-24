package com.surexu.sesame.model.task.yueyuezhuan;

import com.surexu.sesame.data.ConfigV2;
import com.surexu.sesame.data.ModelFields;
import com.surexu.sesame.data.RuntimeInfo;
import com.surexu.sesame.data.modelFieldExt.BooleanModelField;
import com.surexu.sesame.data.modelFieldExt.SelectModelField;
import com.surexu.sesame.entity.CustomOption;
import com.surexu.sesame.entity.IdAndName;
import com.surexu.sesame.model.task.rewardSupport.IsolatedRewardTask;
import com.surexu.sesame.util.Log;
import com.surexu.sesame.util.MessageUtil;
import com.surexu.sesame.util.RandomUtil;
import com.surexu.sesame.util.idMap.UserIdMap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * 月月赚（转账红包）
 *
 * <p>协议于 2026-10-24 在支付宝 12.12.30.8000 上抓包实测并验证（完成"逛余额宝领体验金"后
 * 实际到账 0.01 元转账红包）。完整链路：</p>
 * <ol>
 *   <li>{@code alipay.fundapplication.op.page.recommend}（sceneCode=TRANSFER_LUCK）
 *       -> 页面配置，内含 {@code multiEnvContainer.taskCenInfo} 令牌。</li>
 *   <li>{@code alipay.promoprod.task.listQuery}（带 taskCenInfo 令牌）
 *       -> {@code taskDetailList}：taskType（BROWSER/TRANSFORMER）/ taskProcessStatus /
 *          canAccess / needSignUp / appletId。</li>
 *   <li>{@code alipay.promoprod.applet.trigger}
 *       （appletId + outbizno=AP号+yyyyMMdd + stageCode=send + taskCenInfo）
 *       -> {@code campTriggerInfo.status=SUCCESS} 且 {@code prizeSendInfo} 非空即已发奖。</li>
 *   <li>{@code alipay.promoprod.task.complete.notice}（taskCenInfos=[taskCenInfo]）
 *       -> {@code completeNoticeResponses[].prizeSendOrderList[]} 为入账红包。</li>
 * </ol>
 *
 * <p><b>边界</b>：只自动完成 BROWSER（逛一逛）型任务。TRANSFORMER 型（办卡 / 充话费 /
 * 缴水电 / 买票 / 保险体验）需要真实交易，"每日转账开红包"属于真实支付，均不自动化。</p>
 *
 * <p><b>与 PromoprodRewards 的区别</b>：后者把 taskCenInfo 写死为作者个人令牌，且领奖流程
 * 未实现（源码注释"领取流程待服务端参数确认"）。本任务动态取令牌并补齐 trigger + notice
 * 两步发奖流程。</p>
 */
public final class YueYueZhuan extends IsolatedRewardTask {

    private static final String TAG = "月月赚";

    /** 页面配置接口：下发 taskCenInfo 令牌 */
    private static final String METHOD_PAGE_RECOMMEND = "alipay.fundapplication.op.page.recommend";
    /** 月月赚任务列表（promoprod 体系，必须带 taskCenInfo 令牌才返回 taskDetailList） */
    private static final String METHOD_TASK_LIST = "alipay.promoprod.task.listQuery";
    /** 触发（发奖）接口 */
    private static final String METHOD_APPLET_TRIGGER = "alipay.promoprod.applet.trigger";
    /** 完成上报接口 */
    private static final String METHOD_COMPLETE_NOTICE = "alipay.promoprod.task.complete.notice";

    private static final String SCENE_TRANSFER_LUCK = "TRANSFER_LUCK";
    private static final String BIZ_SCENE_YUEYUEZHUAN = "YUEYUEZHUAN";
    private static final String BIZ_PRODUCT_TRANSFER = "PERSONAL_TRANSFER";
    private static final String TYPE_BROWSER = "BROWSER";
    private static final String STATUS_NOT_DONE = "NOT_DONE";

    /** 黑名单在 ConfigV2 中的模型键 / 字段键 / 展示名（与 addFields 注册保持一致）。 */
    private static final String BLACKLIST_MODEL = "YueYueZhuan";
    private static final String BLACKLIST_FIELD = "YueYueZhuanTaskList";
    private static final String BLACKLIST_TITLE = "月月赚任务";
    /** "每日重置"标记的落盘键（记在 RuntimeInfo，按账号隔离）。 */
    private static final String RESET_DATE_KEY = "YueYueZhuanBlackListResetDate";
    /** 黑名单分页大小（UI 序号与日志分页共用）。 */
    private static final int BLACKLIST_PAGE_SIZE = 8;

    private BooleanModelField browseTasks;
    private BooleanModelField resetBlackListDaily;
    private BooleanModelField simulateBrowse;

    public Boolean getDefaultEnable() {
        return true;
    }


    @Override
    public String getName() {
        return TAG;
    }

    @Override
    protected void addFields(ModelFields modelFields) {
        BooleanModelField browse = new BooleanModelField("browseTasks", "完成逛一逛任务", true);
        this.browseTasks = browse;
        modelFields.addField(browse);

        // 非浏览任务黑名单：候选列表"动态读取当前已拉黑项"，使设置页里能直接看到/取消每个条目。
        // 原先候选列表为空，导致已拉黑清单在 UI 里根本不可见、也无法管理。
        // 每条按 P{页}-{序号} 编号并附〔非浏览〕备注，形成"分页/备注"的展示；
        // 注意：ModelField 加载时会替换 value 引用，故必须经 holder 动态读 getValue()，不能闭包捕获 Set。
        final SelectModelField[] holder = new SelectModelField[1];
        holder[0] = new SelectModelField("YueYueZhuanTaskList",
                "月月赚 | 非浏览任务黑名单", new LinkedHashSet<>(),
                (SelectModelField.SelectListFunc) () -> {
                    Set<String> vals = holder[0] == null ? null : holder[0].getValue();
                    List<IdAndName> list = new ArrayList<>();
                    if (vals != null) {
                        int i = 0;
                        for (String t : vals) {
                            i++;
                            int page = (i - 1) / BLACKLIST_PAGE_SIZE + 1;
                            list.add(new CustomOption(t, "P" + page + "-" + i + " " + t + "〔非浏览〕"));
                        }
                    }
                    return list;
                });
        holder[0].setDescription("非浏览(交易类)任务自动加入；开启“每日重置黑名单重试”后每天清空重试一次。");
        modelFields.addField(holder[0]);

        BooleanModelField resetDaily = new BooleanModelField("resetBlackListDaily", "每日重置黑名单重试", true);
        this.resetBlackListDaily = resetDaily;
        modelFields.addField(resetDaily);

        BooleanModelField browseSim = new BooleanModelField("simulateBrowse", "发完成通知前模拟停留", true);
        this.simulateBrowse = browseSim;
        modelFields.addField(browseSim);
    }

    @Override
    protected void execute(Run run) throws Exception {
        if (!this.browseTasks.getValue()) {
            Log.record(TAG + "：未开启逛一逛任务，本轮跳过");
            return;
        }

        maybeResetBlackListDaily();

        String taskCenInfo = fetchTaskCenInfo(run);
        if (taskCenInfo == null) {
            Log.record(TAG + "：未取到 taskCenInfo 令牌，本轮跳过");
            return;
        }

        JSONArray taskList = fetchTaskList(run, taskCenInfo);
        if (taskList == null) {
            Log.record(TAG + "：未取到任务列表，本轮跳过");
            return;
        }

        int done = 0;
        int skipped = 0;
        int debugLogged = 0;

        // 无上限：遍历全部任务，检索到浏览（BROWSER）类型就做；非浏览类型自动拉黑后跳过。
        for (int i = 0; i < taskList.length(); i++) {
            JSONObject task = taskList.optJSONObject(i);
            if (task == null) {
                continue;
            }
            // promoprod 体系：类型在 taskBaseInfo.appletType（BROWSER=逛一逛浏览 / TRANSFORMER=交易类）
            JSONObject baseInfo = task.optJSONObject("taskBaseInfo");
            String appletType = baseInfo == null ? "" : baseInfo.optString("appletType");
            String status = task.optString("taskProcessStatus", task.optString("status"));
            String appletId = baseInfo == null
                    ? task.optString("appletId", task.optString("taskId"))
                    : baseInfo.optString("appletId", task.optString("appletId", task.optString("taskId")));
            String name = baseInfo == null ? appletId : baseInfo.optString("appletName", appletId);

            if (debugLogged < 6) {
                debugLogged++;
                Log.record(TAG + "：样本#" + i + " appletType=" + appletType + " status=" + status
                        + " appletId=" + appletId + " canAccess=" + task.optBoolean("canAccess")
                        + " needSignUp=" + task.optBoolean("needSignUp"));
            }

            // 非浏览类型（TRANSFORMER/交易类）任务：自动拉黑，后续不再尝试
            if (!TYPE_BROWSER.equals(appletType)) {
                addToBlackList(name);
                skipped++;
                continue;
            }
            if (!STATUS_NOT_DONE.equals(status)) {
                skipped++;
                continue;
            }
            if (!task.optBoolean("canAccess", false)) {
                skipped++;
                continue;
            }
            // 非浏览类任务已在上面统一拉黑；此处浏览类（逛一逛）任务一律执行，
            // 不再因 needSignUp 而跳过或拉黑（用户要求：检索到浏览类型任务就做）。
            if (appletId.isEmpty()) {
                skipped++;
                continue;
            }
            if (doTask(run, taskCenInfo, appletId, name)) {
                done++;
            }
        }

        Log.record(TAG + "：本轮完成逛一逛任务 " + done + " 个，跳过 " + skipped + " 个（非浏览型已自动拉黑）");
        logBlackListPaginated();
    }

    /** 取月月赚黑名单字段（ConfigV2 中已注册的 SelectModelField），取不到返回 null。 */
    private SelectModelField getBlackListField() {
        try {
            ModelFields mf = ConfigV2.INSTANCE.getModelFields(BLACKLIST_MODEL);
            if (mf != null) {
                Object f = mf.get(BLACKLIST_FIELD);
                if (f instanceof SelectModelField) {
                    return (SelectModelField) f;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 拉黑一个非浏览任务；已在黑名单里则不重复写盘/刷日志。 */
    private void addToBlackList(String taskTitle) {
        SelectModelField field = getBlackListField();
        if (field != null && field.contains(taskTitle)) {
            return;
        }
        MessageUtil.MarkTaskBlackList(BLACKLIST_MODEL, BLACKLIST_FIELD, BLACKLIST_TITLE, taskTitle);
    }

    /**
     * 开启“每日重置黑名单重试”时，每天首次运行清空黑名单，让全部任务重新判定/尝试一次
     * （非浏览的会再次被拉黑，浏览的则补做），实现"重试"语义。按账号+日期去重。
     */
    private void maybeResetBlackListDaily() {
        if (this.resetBlackListDaily == null || !this.resetBlackListDaily.getValue()) {
            return;
        }
        String today = today();
        RuntimeInfo rt = RuntimeInfo.getInstance();
        if (today.equals(rt.getString(RESET_DATE_KEY))) {
            return;
        }
        try {
            SelectModelField field = getBlackListField();
            int n = (field == null || field.getValue() == null) ? 0 : field.getValue().size();
            if (field != null && field.getValue() != null) {
                field.clear();
            }
            rt.put(RESET_DATE_KEY, today);
            boolean saved = ConfigV2.save(UserIdMap.getCurrentUid(), false);
            Log.record(TAG + "：每日重置黑名单，清空 " + n + " 个，重新尝试"
                    + (saved ? "" : "（保存失败，本进程内仍生效）"));
        } catch (Throwable t) {
            Log.err(TAG, "每日重置黑名单失败:", t);
        }
    }

    /** 分页打印当前黑名单（每页 {@link #BLACKLIST_PAGE_SIZE} 条），便于日志阅读。 */
    private void logBlackListPaginated() {
        try {
            SelectModelField field = getBlackListField();
            if (field == null || field.getValue() == null || field.getValue().isEmpty()) {
                return;
            }
            List<String> all = new ArrayList<>(field.getValue());
            int pages = (all.size() + BLACKLIST_PAGE_SIZE - 1) / BLACKLIST_PAGE_SIZE;
            for (int p = 0; p < pages; p++) {
                StringBuilder sb = new StringBuilder();
                sb.append(TAG).append("：黑名单 第").append(p + 1).append('/').append(pages).append("页：");
                int from = p * BLACKLIST_PAGE_SIZE;
                int to = Math.min(from + BLACKLIST_PAGE_SIZE, all.size());
                for (int i = from; i < to; i++) {
                    if (i > from) {
                        sb.append("；");
                    }
                    sb.append(all.get(i));
                }
                Log.record(sb.toString());
            }
        } catch (Throwable ignored) {
        }
    }

    /** 取页面令牌：从 page.recommend 响应里递归找 taskCenInfo，避免硬编码（令牌会变）。 */
    private String fetchTaskCenInfo(Run run) throws Exception {
        String data = "[{\"chInfo\":\"ch_appcollect__chsub_my-recentlyUsed\",\"sceneCode\":\""
                + SCENE_TRANSFER_LUCK + "\"}]";
        JSONObject resp = run.query(METHOD_PAGE_RECOMMEND, data);
        if (resp == null) {
            return null;
        }
        return findTaskCenInfo(resp);
    }

    /**
     * 拉月月赚任务列表。
     *
     * <p>实测：必须使用 {@code alipay.promoprod.task.listQuery}（与页面同源），并在请求体里带上
     * {@code taskCenInfo} 令牌；若误用 {@code fundapp.task.summary.query} 只会拿到金额汇总，
     * 不含 taskDetailList。</p>
     */
    private JSONArray fetchTaskList(Run run, String taskCenInfo) throws Exception {
        String data = "[{\"chInfo\":\"ch_appcollect__chsub_my-recentlyUsed\",\"consultAccessFlag\":true,\"taskCenInfo\":\""
                + taskCenInfo + "\"}]";
        JSONObject resp = run.query(METHOD_TASK_LIST, data);
        if (resp == null) {
            return null;
        }
        return resp.optJSONArray("taskDetailList");
    }

    /**
     * 执行单个逛一逛任务：trigger ->（模拟停留）-> complete.notice。
     *
     * @return 是否成功发奖
     */
    private boolean doTask(Run run, String taskCenInfo, String taskId, String taskName) throws Exception {
        String outBizNo = taskId + today();
        String triggerData = "[{\"appletId\":\"" + taskId
                + "\",\"chInfo\":\"ch_appcollect__chsub_my-recentlyUsed\",\"outbizno\":\"" + outBizNo
                + "\",\"stageCode\":\"send\",\"taskCenInfo\":\"" + taskCenInfo + "\"}]";

        JSONObject triggerResp = run.query(METHOD_APPLET_TRIGGER, triggerData);
        if (triggerResp == null) {
            return false;
        }
        JSONObject triggerInfo = triggerResp.optJSONObject("campTriggerInfo");
        boolean triggered = triggerInfo != null && "SUCCESS".equals(triggerInfo.optString("status"));
        if (!triggered) {
            Log.record(TAG + "：触发未成功，" + taskName);
            return false;
        }

        if (this.simulateBrowse.getValue()) {
            // 真实用户会在目标页停留一段时间，随机 8~20 秒，避免"瞬间完成"的可疑特征
            sleepChecked(run, RandomUtil.nextInt(8000, 20000));
        }

        String noticeData = "[{\"chInfo\":\"ch_appcollect__chsub_my-recentlyUsed\",\"taskCenInfo\":\""
                + taskCenInfo + "\",\"taskCenInfos\":[\"" + taskCenInfo + "\"]}]";
        JSONObject noticeResp = run.query(METHOD_COMPLETE_NOTICE, noticeData);
        if (noticeResp == null) {
            return false;
        }

        JSONArray notices = noticeResp.optJSONArray("completeNoticeResponses");
        StringBuilder sb = new StringBuilder();
        if (notices != null) {
            for (int i = 0; i < notices.length(); i++) {
                JSONObject one = notices.optJSONObject(i);
                if (one == null) {
                    continue;
                }
                JSONArray orders = one.optJSONArray("prizeSendOrderList");
                if (orders == null) {
                    continue;
                }
                for (int j = 0; j < orders.length(); j++) {
                    JSONObject order = orders.optJSONObject(j);
                    if (order == null) {
                        continue;
                    }
                    String prize = order.optString("promoPrizeName");
                    if (prize.isEmpty()) {
                        JSONObject ext = order.optJSONObject("extInfo");
                        prize = ext == null ? "红包" : ext.optString("PRIZE_DISPLAY_NAME", "红包");
                    }
                    if (sb.length() > 0) {
                        sb.append("、");
                    }
                    sb.append(prize);
                }
            }
        }
        Log.record(TAG + "：完成[" + taskName + "]，到账：" + (sb.length() == 0 ? "无" : sb.toString()));
        return true;
    }

    /** 分段睡眠，每段都校验任务是否仍应继续（可被停止/切号/离线中断）。 */
    private void sleepChecked(Run run, long millis) throws Exception {
        long left = millis;
        while (left > 0) {
            long step = Math.min(left, 500L);
            Thread.sleep(step);
            left -= step;
            run.requireCurrent();
        }
    }

    /** 递归查找 taskCenInfo 字段，兼容页面配置结构变化。 */
    private static String findTaskCenInfo(Object node) {
        if (node instanceof JSONObject) {
            JSONObject obj = (JSONObject) node;
            String hit = obj.optString("taskCenInfo", "");
            if (!hit.isEmpty()) {
                return hit;
            }
            JSONArray names = obj.names();
            if (names == null) {
                return null;
            }
            for (int i = 0; i < names.length(); i++) {
                String found = findTaskCenInfo(obj.opt(names.optString(i)));
                if (found != null) {
                    return found;
                }
            }
        } else if (node instanceof JSONArray) {
            JSONArray arr = (JSONArray) node;
            for (int i = 0; i < arr.length(); i++) {
                String found = findTaskCenInfo(arr.opt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String today() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.ROOT);
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return sdf.format(new Date());
    }
}
