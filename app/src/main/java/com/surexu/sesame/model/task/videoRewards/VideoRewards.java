package com.surexu.sesame.model.task.videoRewards;

import androidx.core.app.NotificationCompat;
import com.surexu.sesame.data.ModelFields;
import com.surexu.sesame.data.ModelGroup;
import com.surexu.sesame.data.RuntimeInfo;
import com.surexu.sesame.data.modelFieldExt.BooleanModelField;
import com.surexu.sesame.data.modelFieldExt.IntegerModelField;
import com.surexu.sesame.data.task.ModelTask;
import com.surexu.sesame.hook.ApplicationHook;
import com.surexu.sesame.model.base.TaskCommon;
import com.surexu.sesame.util.Log;
import com.surexu.sesame.util.idMap.UserIdMap;
import org.json.JSONArray;
import org.json.JSONObject;

public final class VideoRewards extends ModelTask {
    private static final String NEXT_QUERY = "VideoRewards.nextWalletQuery";
    private IntegerModelField intervalHours;
    private IntegerModelField minimumPlaybackSeconds;
    private BooleanModelField reserve;

    public Boolean getDefaultEnable() {
        return true;
    }


    @Override
    public String getName() {
        return "视频红包钱包查询";
    }

    @Override
    public ModelGroup getGroup() {
        return ModelGroup.OTHER;
    }

    @Override
    public ModelFields getFields() {
        ModelFields modelFields = new ModelFields();
        IntegerModelField integerModelField = new IntegerModelField("intervalHours", "查询间隔(小时)", 6, 1, 24);
        this.intervalHours = integerModelField;
        modelFields.addField(integerModelField);
        IntegerModelField integerModelField2 = new IntegerModelField("minimumPlaybackSeconds", "最小真实播放时长(秒)", 15, 1, 3600);
        this.minimumPlaybackSeconds = integerModelField2;
        modelFields.addField(integerModelField2);
        BooleanModelField booleanModelField = new BooleanModelField("reserve", "预约可用视频任务",true);
        this.reserve = booleanModelField;
        modelFields.addField(booleanModelField);
        return modelFields;
    }

    boolean acceptsObservedState(ObservedVideoState observedVideoState, String str) {
        if (observedVideoState == null || str == null || !str.equals(observedVideoState.contentId)) {
            return false;
        }
        IntegerModelField integerModelField = this.minimumPlaybackSeconds;
        return observedVideoState.qualifies(integerModelField == null ? 15000L : ((long) integerModelField.getValue().intValue()) * 1000);
    }

    @Override
    public Boolean check() {
        String currentUid = UserIdMap.getCurrentUid();
        return Boolean.valueOf((!isEnable().booleanValue() || currentUid == null || currentUid.isEmpty() || TaskCommon.IS_ENERGY_TIME.booleanValue() || ApplicationHook.isOffline() || System.currentTimeMillis() < RuntimeInfo.getInstance().getLong(NEXT_QUERY, 0L).longValue()) ? false : true);
    }

    @Override
    public void run() {
        if (check().booleanValue()) {
            String currentUid = UserIdMap.getCurrentUid();
            RuntimeInfo runtimeInfo = RuntimeInfo.getInstance();
            runtimeInfo.put(NEXT_QUERY, Long.valueOf(System.currentTimeMillis() + (((long) this.intervalHours.getValue().intValue()) * 3600000)));
            if (!currentUid.equals(UserIdMap.getCurrentUid()) || Thread.currentThread().isInterrupted()) {
                return;
            }
            try {
                String strQueryWallet = VideoRewardsRpcCall.queryWallet();
                if (currentUid.equals(UserIdMap.getCurrentUid()) && !Thread.currentThread().isInterrupted()) {
                    if (strQueryWallet != null && !strQueryWallet.isEmpty()) {
                        JSONObject jSONObject = new JSONObject(strQueryWallet);
                        if (jSONObject.optInt("error", 0) == 0 && jSONObject.optBoolean("success", false)) {
                            JSONArray jSONArrayOptJSONArray = jSONObject.optJSONArray("envelopeDetailList");
                            if (jSONArrayOptJSONArray == null) {
                                Log.record("视频红包钱包查询：响应缺少红包列表，停止解析");
                                return;
                            }
                            boolean zOptBoolean = false;
                            int i = 0;
                            int i2 = 0;
                            int i3 = 0;
                            for (int i4 = 0; i4 < jSONArrayOptJSONArray.length(); i4++) {
                                JSONObject jSONObjectOptJSONObject = jSONArrayOptJSONArray.optJSONObject(i4);
                                if (jSONObjectOptJSONObject == null) {
                                    i++;
                                } else {
                                    zOptBoolean |= jSONObjectOptJSONObject.optBoolean("hasMore", false);
                                    JSONArray jSONArrayOptJSONArray2 = jSONObjectOptJSONObject.optJSONArray("envelopeVOList");
                                    if (jSONArrayOptJSONArray2 != null) {
                                        for (int i5 = 0; i5 < jSONArrayOptJSONArray2.length(); i5++) {
                                            i2++;
                                            JSONObject jSONObjectOptJSONObject2 = jSONArrayOptJSONArray2.optJSONObject(i5);
                                            if (jSONObjectOptJSONObject2 == null || !"progressing".equals(jSONObjectOptJSONObject2.optString(NotificationCompat.CATEGORY_STATUS))) {
                                                i++;
                                            } else {
                                                i3++;
                                            }
                                        }
                                    }
                                }
                            }
                            Log.record("视频红包钱包查询：首屏红包=" + i2 + "，进行中=" + i3 + "，其他或未知状态=" + i + "，还有分页=" + zOptBoolean);
                            if (this.reserve.getValue().booleanValue()) {
                                String strReserve = VideoRewardsRpcCall.reserve();
                                if (strReserve != null && strReserve.contains("\"success\":true")) {
                                    runtimeInfo.put("VideoRewards.reserveAttempt", Long.valueOf(System.currentTimeMillis()));
                                    Log.record("视频红包：预约接口返回成功");
                                    return;
                                } else {
                                    Log.record("视频红包：预约接口未明确成功，本轮不重试");
                                    return;
                                }
                            }
                            return;
                        }
                        if (jSONObject.optInt("error", 0) == 1009) {
                            runtimeInfo.put(NEXT_QUERY, Long.valueOf(System.currentTimeMillis() + 86400000));
                            Log.record("视频红包钱包查询：访问被拒绝，暂停该功能24小时");
                            return;
                        } else {
                            Log.record("视频红包钱包查询：接口未返回成功，本轮结束");
                            return;
                        }
                    }
                    Log.record("视频红包钱包查询：无响应，本轮结束");
                }
            } catch (Exception e) {
                Log.record("视频红包钱包查询异常：" + e.getClass().getSimpleName());
            }
        }
    }
}
