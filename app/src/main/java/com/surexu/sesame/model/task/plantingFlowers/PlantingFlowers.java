package com.surexu.sesame.model.task.plantingFlowers;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.surexu.sesame.data.ModelFields;
import com.surexu.sesame.data.ModelGroup;
import com.surexu.sesame.data.RuntimeInfo;
import com.surexu.sesame.data.modelFieldExt.BooleanModelField;
import com.surexu.sesame.data.modelFieldExt.StringModelField;
import com.surexu.sesame.data.task.ModelTask;
import com.surexu.sesame.model.base.TaskCommon;
import com.surexu.sesame.util.Log;
import com.surexu.sesame.util.TimeUtil;

public class PlantingFlowers extends ModelTask {

    private static final String TAG = PlantingFlowers.class.getSimpleName();
    private static final String DISPLAY_NAME = "种花大作战";

    /** 主流程执行间隔（毫秒） */
    private static final long RUN_INTERVAL = 4 * 60 * 60 * 1000L;
    /** 日常任务黑名单 taskType */
    private static final int TASK_TYPE_BLACK_MIN = 17;
    private static final int TASK_TYPE_BLACK_MAX = 25;
    /** 口令池 key */
    private static final String ASSIST_KEY = "PlantingFlowersShare";

    private BooleanModelField plantingFlowers;
    private BooleanModelField shareEnable;
    private StringModelField manualToken;
    private StringModelField manualSecret;
    private StringModelField assistListUrl;
    private StringModelField assistPushUrl;
    private StringModelField recordPushUrl;

    public Boolean getDefaultEnable() {
        return true;
    }


    @Override
    public String getName() {
        return DISPLAY_NAME;
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        modelFields.addField(plantingFlowers = new BooleanModelField("plantingFlowers", "种花大作战 | 自动任务",true));
        modelFields.addField(shareEnable = new BooleanModelField("shareEnable", "种花大作战 | 口令助力",true));
        modelFields.addField(manualToken = new StringModelField("plantingFlowersManualToken", "种花大作战 | 手动Token(可选)", ""));
        modelFields.addField(manualSecret = new StringModelField("plantingFlowersManualSecret", "种花大作战 | 手动助力口令(可选)", ""));
        modelFields.addField(assistListUrl = new StringModelField("plantingFlowersAssistListUrl", "种花大作战 | 口令池-取列表URL", ""));
        modelFields.addField(assistPushUrl = new StringModelField("plantingFlowersAssistPushUrl", "种花大作战 | 口令池-上传URL", ""));
        modelFields.addField(recordPushUrl = new StringModelField("plantingFlowersRecordPushUrl", "种花大作战 | 口令池-记录URL", ""));
        return modelFields;
    }

    @Override
    public Boolean check() {
        if (TaskCommon.IS_ENERGY_TIME) {
            return false;
        }
        long executeTime = RuntimeInfo.getInstance().getLong("plantingFlowers", 0);
        return System.currentTimeMillis() - executeTime >= RUN_INTERVAL;
    }

    @Override
    public void run() {
        try {
            if (!plantingFlowers.getValue()) {
                return;
            }
            RuntimeInfo.getInstance().put("plantingFlowers", System.currentTimeMillis());

            PlantingFlowersRpcCall.LoginSession session = PlantingFlowersRpcCall.login(manualToken.getValue());
            if (session == null) {
                Log.other(DISPLAY_NAME + " 登录失败，跳过本轮");
                return;
            }
            if (session.uid.isEmpty()) {
                Log.other(DISPLAY_NAME + " 未获取到 uid，口令助力与日常任务将跳过");
            }

            doNewUserReward(session);
            doShareAssist(session);
            collectBeans(session);
            doTomorrowReward(session);
            doSignIn(session);
            doWeeding(session);
            doCloudReward(session);
            doBugWormReward(session);
            doFeedFactory(session);
            doDailyDraw(session);
            doTasks(session);
            doAcceleratorCardTasks(session);
            doWatering(session);
            collectBeans(session);
        } catch (Throwable t) {
            Log.err(TAG, DISPLAY_NAME + " start.run err:", t);
        }
    }

    // ═══════════════════ 新人奖励 ═══════════════════

    private void doNewUserReward(PlantingFlowersRpcCall.LoginSession session) {
        try {
            JSONObject res = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/acceleratorCard/newUserReward", "POST",
                    new JSONObject(), session.token, session.uid);
            if (PlantingFlowersRpcCall.isOk(res)) {
                Log.other(DISPLAY_NAME + " 新人加速卡奖励已领取");
            } else {
                String msg = PlantingFlowersRpcCall.extractMessage(res);
                if (!msg.isEmpty() && !"null".equals(msg)) {
                    Log.other(DISPLAY_NAME + " 新人奖励: " + msg);
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "newUserReward err:", t);
        }
    }

    // ═══════════════════ 口令助力（口令池可选） ═══════════════════

    private void doShareAssist(PlantingFlowersRpcCall.LoginSession session) {
        try {
            if (!shareEnable.getValue()) {
                return;
            }
            // 1. 获取本账号口令（message 优先，兼容 obj）
            String mySec = null;
            JSONObject secRes = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/getShareSecret", "POST",
                    new JSONObject(), session.token, session.uid);
            if (secRes != null) {
                String msg = PlantingFlowersRpcCall.extractMessage(secRes);
                if (!msg.isEmpty()) {
                    mySec = msg;
                } else {
                    Object obj = PlantingFlowersRpcCall.extractObjOrArray(secRes);
                    if (obj != null) {
                        mySec = obj.toString();
                    }
                }
            }

            // 2. 上传本账号口令至口令池
            String pushUrl = assistPushUrl.getValue();
            if (mySec != null && !pushUrl.isEmpty() && !session.uid.isEmpty()) {
                try {
                    JSONObject map = new JSONObject();
                    map.put("shareId", mySec);
                    PlantingFlowersRpcCall.requestRaw(pushUrl + ASSIST_KEY + "/" + session.uid,
                            "POST", map.toString(), simpleHeaders(session));
                } catch (Throwable ignored) {
                }
            }

            // 3. 检查今日是否已达助力上限
            if (isShareRecallLimited(session)) {
                Log.other(DISPLAY_NAME + " 今日助力他人次数已达上限");
                return;
            }

            // 4. 获取待助力列表（口令池 URL 直接拼 ASSIST_KEY/uid，响应为 JSON 数组）
            JSONArray targetList = new JSONArray();
            String listUrl = assistListUrl.getValue();
            if (!listUrl.isEmpty() && !session.uid.isEmpty()) {
                try {
                    String listStr = PlantingFlowersRpcCall.requestRaw(
                            listUrl + ASSIST_KEY + "/" + session.uid, "GET", null, simpleHeaders(session));
                    if (listStr != null && !listStr.isEmpty()) {
                        Object parsed = new JSONArray(listStr);
                        if (parsed instanceof JSONArray) {
                            targetList = (JSONArray) parsed;
                        }
                    }
                } catch (Throwable t) {
                    Log.err(TAG, "口令池列表解析失败:", t);
                }
            }

            // 5. 手动口令并入列表
            String manualSecretVal = manualSecret.getValue();
            if (manualSecretVal != null && !manualSecretVal.isEmpty()) {
                boolean exists = false;
                for (int i = 0; i < targetList.length(); i++) {
                    JSONObject item = targetList.optJSONObject(i);
                    if (item != null) {
                        String sid = item.optString("shareId", "");
                        if (sid.isEmpty()) {
                            sid = item.optString("shareSecret", "");
                        }
                        if (manualSecretVal.equals(sid)) {
                            exists = true;
                            break;
                        }
                    }
                }
                if (!exists) {
                    JSONObject manual = new JSONObject();
                    manual.put("shareId", manualSecretVal);
                    manual.put("userId", "manual");
                    JSONArray merged = new JSONArray();
                    merged.put(manual);
                    for (int i = 0; i < targetList.length(); i++) {
                        merged.put(targetList.opt(i));
                    }
                    targetList = merged;
                }
            }

            if (targetList.length() == 0) {
                Log.other(DISPLAY_NAME + " 暂无待助力好友口令");
                return;
            }

            // 6. 逐条助力
            JSONArray taskRecords = new JSONArray();
            int remainHelpCount = 3;
            for (int i = 0; i < targetList.length(); i++) {
                if (remainHelpCount <= 0) {
                    break;
                }
                JSONObject item = targetList.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String targetSecret = item.optString("shareId", "");
                if (targetSecret.isEmpty()) {
                    targetSecret = item.optString("shareSecret", "");
                }
                if (targetSecret.isEmpty()) {
                    targetSecret = item.optString("secret", "");
                }
                String targetUser = item.optString("userId", "");
                if (targetUser.isEmpty()) {
                    targetUser = "friend";
                }
                if (targetSecret.isEmpty() || targetSecret.equals(mySec)) {
                    continue;
                }

                JSONObject body = new JSONObject();
                body.put("shareSecret", targetSecret);
                JSONObject assistRes = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/clickFriendShare/addFriend", "POST",
                        body, session.token, session.uid);
                Object assistObj = PlantingFlowersRpcCall.extractObjOrArray(assistRes);
                String state = "";
                if (assistObj instanceof JSONObject) {
                    state = ((JSONObject) assistObj).optString("state", "");
                }
                if (state.isEmpty()) {
                    state = PlantingFlowersRpcCall.extractMessage(assistRes);
                }
                JSONObject tr = new JSONObject();
                tr.put("userId", targetUser);
                tr.put("shareId", targetSecret);
                tr.put("state", 0);

                if ("success".equals(state) || PlantingFlowersRpcCall.isOk(assistRes)) {
                    tr.put("state", 2);
                    remainHelpCount--;
                    Log.other(DISPLAY_NAME + " 助力成功: 为好友 [" + targetUser + "] 助力");
                } else if ("today_have_help".equals(state)) {
                    Log.other(DISPLAY_NAME + " 今日已帮好友 [" + targetUser + "] 助力过");
                } else if ("helper_to_limit".equals(state)) {
                    Log.other(DISPLAY_NAME + " 今日助力他人次数已达上限");
                    markShareRecallLimited(session);
                    taskRecords.put(tr);
                    break;
                } else if ("inviter_to_upper_limit".equals(state)) {
                    Log.other(DISPLAY_NAME + " 好友 [" + targetUser + "] 今日被助力次数已达上限");
                } else if ("inviter_eq_helper".equals(state)) {
                    // 自己无需助力
                } else {
                    remainHelpCount--;
                    if (!state.isEmpty()) {
                        Log.other(DISPLAY_NAME + " 为 [" + targetUser + "] 助力反馈: " + state);
                    }
                }
                taskRecords.put(tr);
                TimeUtil.sleep(1000);
            }

            // 7. 上报助力执行记录给口令池
            String recordUrl = recordPushUrl.getValue();
            if (taskRecords.length() > 0 && !recordUrl.isEmpty() && !session.uid.isEmpty()) {
                try {
                    PlantingFlowersRpcCall.requestRaw(recordUrl + ASSIST_KEY + "/" + session.uid,
                            "POST", taskRecords.toString(), simpleHeaders(session));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "shareAssist err:", t);
        }
    }

    private boolean isShareRecallLimited(PlantingFlowersRpcCall.LoginSession session) {
        String key = "plantingFlowersShareRecall|" + (session.uid.isEmpty() ? session.token : session.uid);
        return RuntimeInfo.getInstance().getLong(key, 0) == todayLong();
    }

    private void markShareRecallLimited(PlantingFlowersRpcCall.LoginSession session) {
        String key = "plantingFlowersShareRecall|" + (session.uid.isEmpty() ? session.token : session.uid);
        RuntimeInfo.getInstance().put(key, todayLong());
    }

    private long todayLong() {
        return Long.parseLong(new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date()));
    }

    // ═══════════════════ 收花豆 ═══════════════════

    private void collectBeans(PlantingFlowersRpcCall.LoginSession session) {
        try {
            JSONObject res = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/collect/goldenBeans", "POST",
                    new JSONObject(), session.token, session.uid);
            if (PlantingFlowersRpcCall.isOk(res)) {
                Log.other(DISPLAY_NAME + " 收取花豆成功");
            }
        } catch (Throwable t) {
            Log.err(TAG, "collectBeans err:", t);
        }
    }

    // ═══════════════════ 明日奖励 ═══════════════════

    private void doTomorrowReward(PlantingFlowersRpcCall.LoginSession session) {
        try {
            JSONObject res = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/waterTomorrowReward/query", "GET",
                    null, session.token, session.uid);
            JSONObject obj = PlantingFlowersRpcCall.extractObj(res);
            if (obj == null) {
                return;
            }
            if (obj.optInt("yesterdayFertilizationReward") > 0 && !obj.optBoolean("todayIsReceived")) {
                JSONObject body = new JSONObject();
                body.put("rewardSource", 3);
                JSONObject rewardRes = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/waterTomorrowReward/receivedFeed", "POST",
                        body, session.token, session.uid);
                if (PlantingFlowersRpcCall.isOk(rewardRes)) {
                    Log.other(DISPLAY_NAME + " 昨日施肥奖励已领取");
                } else {
                    Log.other(DISPLAY_NAME + " 昨日施肥奖励领取失败: " + PlantingFlowersRpcCall.extractMessage(rewardRes));
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "tomorrowReward err:", t);
        }
    }

    // ═══════════════════ 签到 ═══════════════════

    private void doSignIn(PlantingFlowersRpcCall.LoginSession session) {
        try {
            JSONObject res = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/signTask/querySignInStatus", "GET",
                    null, session.token, session.uid);
            JSONObject obj = PlantingFlowersRpcCall.extractObj(res);
            if (obj == null) {
                return;
            }
            if (obj.optBoolean("todayAlreadySign")) {
                Log.other(DISPLAY_NAME + " 今日已签到");
                return;
            }
            JSONObject body = new JSONObject();
            body.put("rewardSource", 3);
            JSONObject signRes = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/signTask/finish2", "POST",
                    body, session.token, session.uid);
            if (PlantingFlowersRpcCall.isOk(signRes)) {
                Log.other(DISPLAY_NAME + " 签到成功");
            } else {
                Log.other(DISPLAY_NAME + " 签到失败: " + PlantingFlowersRpcCall.extractMessage(signRes));
            }
        } catch (Throwable t) {
            Log.err(TAG, "signIn err:", t);
        }
    }

    // ═══════════════════ 除草 ═══════════════════

    private void doWeeding(PlantingFlowersRpcCall.LoginSession session) {
        try {
            JSONObject body = new JSONObject();
            body.put("taskSource", 1);
            JSONObject res = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/sendWeedingReward", "POST",
                    body, session.token, session.uid);
            if (PlantingFlowersRpcCall.isOk(res)) {
                Log.other(DISPLAY_NAME + " 除草奖励已领取");
            }
        } catch (Throwable t) {
            Log.err(TAG, "weeding err:", t);
        }
    }

    // ═══════════════════ 云朵奖励 ═══════════════════

    private void doCloudReward(PlantingFlowersRpcCall.LoginSession session) {
        try {
            JSONObject res = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/cloudRewardConfig", "GET",
                    null, session.token, session.uid);
            JSONArray cloudList = toArrayOrContainerArray(res);
            if (cloudList == null || cloudList.length() == 0) {
                return;
            }
            for (int i = 0; i < cloudList.length(); i++) {
                JSONObject item = cloudList.optJSONObject(i);
                if (item == null || !item.optBoolean("currently") || item.optBoolean("isFinish")) {
                    continue;
                }
                JSONObject body = new JSONObject();
                body.put("rewardType", 4);
                body.put("type", 2);
                JSONObject rewardRes = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/bugReward", "POST",
                        body, session.token, session.uid);
                Object rewObj = PlantingFlowersRpcCall.extractObjOrArray(rewardRes);
                int feeds = 0;
                if (rewObj instanceof JSONObject) {
                    feeds = ((JSONObject) rewObj).optInt("feeds", 0);
                }
                if (feeds > 0) {
                    Log.other(DISPLAY_NAME + " 收获云朵水滴: +" + feeds + "ml");
                }
                TimeUtil.sleep(600);
            }
        } catch (Throwable t) {
            Log.err(TAG, "cloudReward err:", t);
        }
    }

    // ═══════════════════ 虫害奖励 ═══════════════════

    private void doBugWormReward(PlantingFlowersRpcCall.LoginSession session) {
        try {
            JSONObject res = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/bugRewardConfig", "GET",
                    null, session.token, session.uid);
            JSONArray bugList = toArrayOrContainerArray(res);
            if (bugList == null || bugList.length() == 0) {
                return;
            }
            for (int i = 0; i < bugList.length(); i++) {
                JSONObject item = bugList.optJSONObject(i);
                if (item == null || !item.optBoolean("currently")) {
                    continue;
                }
                if (item.optInt("rewardNum") <= 0 || item.optBoolean("alreadyFinish")) {
                    continue;
                }
                JSONObject body = new JSONObject();
                body.put("rewardType", 1);
                JSONObject rewardRes = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/bugReward", "POST",
                        body, session.token, session.uid);
                Object rewObj = PlantingFlowersRpcCall.extractObjOrArray(rewardRes);
                int feeds = 0;
                if (rewObj instanceof JSONObject) {
                    feeds = ((JSONObject) rewObj).optInt("feeds", 0);
                }
                if (feeds > 0) {
                    Log.other(DISPLAY_NAME + " 驱赶小蜜蜂/捉虫: +" + feeds + "ml水滴");
                }
                TimeUtil.sleep(600);
            }
        } catch (Throwable t) {
            Log.err(TAG, "bugWormReward err:", t);
        }
    }

    /** 响应 obj 为数组则直接用；兼容 obj 为 {currently:[...]} 容器 */
    private JSONArray toArrayOrContainerArray(JSONObject res) {
        Object obj = PlantingFlowersRpcCall.extractObjOrArray(res);
        if (obj instanceof JSONArray) {
            return (JSONArray) obj;
        }
        if (obj instanceof JSONObject) {
            return ((JSONObject) obj).optJSONArray("currently");
        }
        return null;
    }

    // ═══════════════════ 饲料厂 ═══════════════════

    private void doFeedFactory(PlantingFlowersRpcCall.LoginSession session) {
        try {
            JSONObject res = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/feedFactory/info", "GET",
                    null, session.token, session.uid);
            JSONObject obj = PlantingFlowersRpcCall.extractObj(res);
            if (obj == null) {
                return;
            }
            int todayCount = obj.optInt("todayCount");
            if (todayCount < 5) {
                long endMs = parseEndTimeMs(obj.optString("endTime", ""));
                if (endMs > 0 && (endMs - System.currentTimeMillis()) <= 1000) {
                    JSONObject body = new JSONObject();
                    body.put("rewardSource", 1);
                    JSONObject rewardRes = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/feedFactory/reward", "POST",
                            body, session.token, session.uid);
                    Object rewObj = PlantingFlowersRpcCall.extractObjOrArray(rewardRes);
                    if (rewObj instanceof JSONObject) {
                        Log.other(DISPLAY_NAME + " 水滴加工厂生产完成: +" + ((JSONObject) rewObj).optInt("feeds", 0) + "ml水滴");
                    }
                }
            } else if (todayCount >= 5) {
                Log.other(DISPLAY_NAME + " 饲料厂今日已达上限");
            }
        } catch (Throwable t) {
            Log.err(TAG, "feedFactory err:", t);
        }
    }

    /** 解析 "yyyy-MM-dd HH:mm:ss"（兼容 yyyy/MM/dd 分隔）→ 毫秒，失败返回 0 */
    private long parseEndTimeMs(String endTime) {
        if (endTime == null || endTime.isEmpty()) {
            return 0;
        }
        try {
            String normalized = endTime.replace("-", "/");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.ROOT);
            return sdf.parse(normalized).getTime();
        } catch (Throwable t) {
            Log.err(TAG, "endTime 解析失败: " + endTime, t);
            return 0;
        }
    }

    // ═══════════════════ 每日抽奖 ═══════════════════

    private void doDailyDraw(PlantingFlowersRpcCall.LoginSession session) {
        try {
            JSONObject res = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/dailyDraw/activity/info", "GET",
                    null, session.token, session.uid);
            JSONObject obj = PlantingFlowersRpcCall.extractObj(res);
            if (obj == null) {
                return;
            }
            int used = obj.optInt("activityUseNumber");
            int max = obj.optInt("activityMaxNumber");
            int remain = max - used;
            if (remain <= 0) {
                Log.other(DISPLAY_NAME + " 今日抽奖次数已用完");
                return;
            }
            int ok = 0;
            while (remain > 0) {
                JSONObject drawRes = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/dailyDraw/lottery", "GET",
                        null, session.token, session.uid);
                Object drawObj = PlantingFlowersRpcCall.extractObjOrArray(drawRes);
                if (PlantingFlowersRpcCall.isOk(drawRes) && drawObj instanceof JSONObject) {
                    String prizeName = ((JSONObject) drawObj).optString("awardName", "");
                    if (prizeName.isEmpty()) {
                        prizeName = ((JSONObject) drawObj).optInt("awardPrice", 0) > 0
                                ? ((JSONObject) drawObj).optInt("awardPrice") + "水滴" : "奖励";
                    }
                    Log.other(DISPLAY_NAME + " 扭蛋抽奖成功: 获得 [" + prizeName + "]");
                    ok++;
                    remain--;
                } else {
                    break;
                }
                TimeUtil.sleep(1000);
            }
            if (ok > 0) {
                Log.other(DISPLAY_NAME + " 每日抽奖完成 " + ok + " 次");
            }
        } catch (Throwable t) {
            Log.err(TAG, "dailyDraw err:", t);
        }
    }

    // ═══════════════════ 日常任务（数据中心） ═══════════════════

    private void doTasks(PlantingFlowersRpcCall.LoginSession session) {
        try {
            if (session.uid.isEmpty()) {
                Log.other(DISPLAY_NAME + " 未登录获取uid，跳过日常任务");
                return;
            }
            String listUrl = PlantingFlowersRpcCall.DATA_CENTER_HOST + "/dataCenter/api/task/list?mark=PLANTING_FLOWERS_JJEGG_LIST&uid="
                    + URLEncoder.encode(session.uid, StandardCharsets.UTF_8.name()) + "&versions=2";
            JSONObject res = PlantingFlowersRpcCall.requestApi(listUrl, "GET", null, session.token, session.uid);
            Object dataObj = PlantingFlowersRpcCall.extractObjOrArray(res);
            if (dataObj == null) {
                Log.other(DISPLAY_NAME + " 日常任务列表获取失败");
                return;
            }
            String dataCenterUserId = "";
            JSONArray taskList = null;
            if (dataObj instanceof JSONArray) {
                taskList = (JSONArray) dataObj;
            } else if (dataObj instanceof JSONObject) {
                JSONObject dobj = (JSONObject) dataObj;
                dataCenterUserId = dobj.optString("userId", "");
                taskList = dobj.optJSONArray("taskList");
            }
            if (taskList == null || taskList.length() == 0) {
                Log.other(DISPLAY_NAME + " 暂无日常任务");
                return;
            }
            int done = 0;
            for (int i = 0; i < taskList.length(); i++) {
                JSONObject task = taskList.optJSONObject(i);
                if (task == null) {
                    continue;
                }
                String taskId = task.optString("id", "");
                if (taskId.isEmpty()) {
                    taskId = task.optString("taskId", "");
                }
                int taskType = task.optInt("taskType");
                if (taskId.isEmpty() || taskType == 0) {
                    continue;
                }
                if (taskType >= TASK_TYPE_BLACK_MIN && taskType <= TASK_TYPE_BLACK_MAX) {
                    continue;
                }
                if (task.optBoolean("alreadyFinish") || task.optBoolean("finished")) {
                    continue;
                }
                // 1. 任务点击上报（数据中心）
                JSONObject clickBody = new JSONObject();
                clickBody.put("uid", session.uid);
                clickBody.put("taskId", taskId);
                clickBody.put("mark", "PLANTING_FLOWERS_JJEGG_LIST");
                PlantingFlowersRpcCall.requestApi(PlantingFlowersRpcCall.DATA_CENTER_HOST + "/dataCenter/api/task/click", "POST",
                        clickBody, session.token, session.uid);
                // 2. 模拟浏览停留耗时（按任务配置 visitSeconds，至多 3 秒）
                int visitSec = task.optInt("visitSeconds", 2);
                if (visitSec <= 0) {
                    visitSec = 2;
                }
                TimeUtil.sleep(Math.min(visitSec, 3) * 1000L);
                // 3. 上报动作完成（数据中心，GET 拼 query）
                String finishUrl = PlantingFlowersRpcCall.DATA_CENTER_HOST + "/dataCenter/api/task/action/finish?uid="
                        + URLEncoder.encode(session.uid, StandardCharsets.UTF_8.name())
                        + "&taskId=" + URLEncoder.encode(taskId, StandardCharsets.UTF_8.name())
                        + "&mark=" + URLEncoder.encode("PLANTING_FLOWERS_JJEGG_LIST", StandardCharsets.UTF_8.name());
                PlantingFlowersRpcCall.requestApi(finishUrl, "GET", null, session.token, session.uid);
                TimeUtil.sleep(800);
                // 4. 领取任务水滴奖励（业务端）
                JSONObject recordBody = new JSONObject();
                recordBody.put("isReward", true);
                recordBody.put("taskId", taskId);
                recordBody.put("taskType", taskType);
                recordBody.put("outUserId", dataCenterUserId);
                recordBody.put("positionMark", "PLANTING_FLOWERS_JJEGG_LIST");
                PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/task/record", "POST",
                        recordBody, session.token, session.uid);
                done++;
            }
            Log.other(DISPLAY_NAME + " 日常任务完成 " + done + " 个");
        } catch (Throwable t) {
            Log.err(TAG, "doTasks err:", t);
        }
    }

    // ═══════════════════ 加速卡任务 ═══════════════════

    private void doAcceleratorCardTasks(PlantingFlowersRpcCall.LoginSession session) {
        try {
            int[][] configs = {{1, 5, 2}, {2, 10, 3}, {3, 15, 3}};
            for (int[] cfg : configs) {
                JSONObject body = new JSONObject();
                body.put("type", cfg[0]);
                body.put("isFinish", false);
                JSONObject res = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/receive/acceleratorCard", "POST",
                        body, session.token, session.uid);
                JSONObject obj = PlantingFlowersRpcCall.extractObj(res);
                if (obj == null) {
                    continue;
                }
                if (obj.optBoolean("state") && !obj.optBoolean("alreadyFinish")) {
                    JSONObject finishBody = new JSONObject();
                    finishBody.put("type", cfg[0]);
                    finishBody.put("isFinish", true);
                    JSONObject finishRes = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/receive/acceleratorCard", "POST",
                            finishBody, session.token, session.uid);
                    if (PlantingFlowersRpcCall.isOk(finishRes)) {
                        Log.other(DISPLAY_NAME + " 加速卡任务(type=" + cfg[0] + ")完成");
                    }
                }
            }
        } catch (Throwable t) {
            Log.err(TAG, "acceleratorCardTasks err:", t);
        }
    }

    // ═══════════════════ 浇水 + 预约浇水 ═══════════════════

    private void doWatering(PlantingFlowersRpcCall.LoginSession session) {
        try {
            JSONObject dev = queryDevelop(session);
            if (dev == null) {
                Log.other(DISPLAY_NAME + " 获取生长信息失败");
                return;
            }
            logDevelop(dev);
            // 吸收期且持有加速卡 → 使用加速卡
            if (fastCardCount(dev) > 0 && dev.optInt("isWatering") == 1) {
                JSONObject useRes = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/use/acceleratorCard", "POST",
                        new JSONObject(), session.token, session.uid);
                if (PlantingFlowersRpcCall.isOk(useRes)) {
                    Log.other(DISPLAY_NAME + " 已使用加速卡");
                }
                TimeUtil.sleep(1500);
                dev = queryDevelop(session);
                if (dev == null) {
                    return;
                }
            }
            int waterNum = dev.optInt("numberWater");
            int isWatering = dev.optInt("isWatering");
            long endWaterTime = dev.optLong("endWaterTime");
            if (waterNum >= 100) {
                JSONObject body = new JSONObject();
                body.put("isWaterTomorrow", true);
                JSONObject waterRes = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/water/coffeeTree", "POST",
                        body, session.token, session.uid);
                if (PlantingFlowersRpcCall.isOk(waterRes)) {
                    Log.other(DISPLAY_NAME + " 浇水成功，等待下一次生长");
                    collectBeans(session);
                    TimeUtil.sleep(1000);
                    JSONObject fresh = queryDevelop(session);
                    if (fresh != null) {
                        isWatering = fresh.optInt("isWatering");
                        endWaterTime = fresh.optLong("endWaterTime");
                    } else {
                        isWatering = 0;
                    }
                } else {
                    Log.other(DISPLAY_NAME + " 浇水失败: " + PlantingFlowersRpcCall.extractMessage(waterRes));
                }
            } else {
                Log.other(DISPLAY_NAME + " 水滴不足(" + waterNum + "/100)，暂不浇水");
            }
            if (isWatering == 1 && endWaterTime > 0) {
                scheduleWater(session, endWaterTime);
            }
        } catch (Throwable t) {
            Log.err(TAG, "watering err:", t);
        }
    }

    private JSONObject queryDevelop(PlantingFlowersRpcCall.LoginSession session) {
        JSONObject res = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/coffeeBeans/develop", "GET",
                null, session.token, session.uid);
        return PlantingFlowersRpcCall.extractObj(res);
    }

    private void logDevelop(JSONObject dev) {
        int beans = dev.optInt("goldenBeansNumber");
        if (beans == 0) {
            beans = dev.optInt("numberCoffeeBeans");
        }
        int waterNum = dev.optInt("numberWater");
        int progress = dev.optInt("coffeeBeansProgress");
        int fastCards = fastCardCount(dev);
        Log.other(DISPLAY_NAME + " 花豆:" + beans + "颗 | 水滴:" + waterNum + "ml | 生长进度:" + progress + "% | 加速卡:" + fastCards + "张");
    }

    /** 加速卡数量：acceleratorCardNumber 优先，兼容 accelerateCard */
    private int fastCardCount(JSONObject dev) {
        int count = dev.optInt("acceleratorCardNumber");
        if (count == 0) {
            count = dev.optInt("accelerateCard");
        }
        return count;
    }

    /**
     * 预约浇水：在 endWaterTime 时刻后 5s 触发一次浇水（参照脚本 timerDelay + 5000）
     * 同一 id 的旧任务会被替换，天然去重
     */
    private void scheduleWater(PlantingFlowersRpcCall.LoginSession session, long endWaterTime) {
        long execTime = endWaterTime + 5000;
        String id = "PlantingWater|" + (session.uid.isEmpty() ? session.token : session.uid) + "|" + endWaterTime;
        addChildTask(new ChildModelTask(id, execTime) {
            @Override
            public Runnable setRunnable() {
                return () -> {
                    try {
                        Log.other(DISPLAY_NAME + " 预约浇水触发");
                        PlantingFlowersRpcCall.LoginSession newSession = PlantingFlowersRpcCall.login(manualToken.getValue());
                        if (newSession == null) {
                            Log.other(DISPLAY_NAME + " 预约浇水登录失败");
                            return;
                        }
                        JSONObject dev = queryDevelop(newSession);
                        if (dev == null) {
                            return;
                        }
                        if (dev.optInt("numberWater") >= 100) {
                            JSONObject body = new JSONObject();
                            body.put("isWaterTomorrow", true);
                            JSONObject waterRes = PlantingFlowersRpcCall.requestApi("/plantingFlowers/api/water/coffeeTree", "POST",
                                    body, newSession.token, newSession.uid);
                            if (PlantingFlowersRpcCall.isOk(waterRes)) {
                                Log.other(DISPLAY_NAME + " 预约浇水成功");
                                collectBeans(newSession);
                            } else {
                                Log.other(DISPLAY_NAME + " 预约浇水失败: " + PlantingFlowersRpcCall.extractMessage(waterRes));
                            }
                        } else {
                            Log.other(DISPLAY_NAME + " 预约时水滴不足: " + dev.optInt("numberWater") + "/100");
                        }
                    } catch (Throwable t) {
                        Log.err(TAG, "预约浇水异常:", t);
                    }
                };
            }
        });
        Log.other(DISPLAY_NAME + " 已预约浇水(吸收结束触发)");
    }

    // ═══════════════════ 工具 ═══════════════════

    private JSONObject simpleHeaders(PlantingFlowersRpcCall.LoginSession session) throws JSONException {
        JSONObject headers = new JSONObject();
        headers.put("Content-Type", "application/json;charset=UTF-8");
        headers.put("channel", "fenxiang");
        if (session.token != null && !session.token.isEmpty()) {
            headers.put("token", session.token);
        }
        if (session.uid != null && !session.uid.isEmpty()) {
            headers.put("uid", session.uid);
        }
        return headers;
    }
}
