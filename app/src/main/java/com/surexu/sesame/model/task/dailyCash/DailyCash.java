package com.surexu.sesame.model.task.dailyCash;

import com.surexu.sesame.data.ModelFields;
import com.surexu.sesame.data.modelFieldExt.BooleanModelField;
import com.surexu.sesame.model.task.rewardSupport.IsolatedRewardTask;
import com.surexu.sesame.util.Log;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

public final class DailyCash extends IsolatedRewardTask {
    private static final String PREFIX = "alipay.membertangram.biz.rpc.newtaskcenter.";
    private static final String QUERY = "[{\"activityId\":\"SIGN_TASK_CENTER\",\"source\":\"sousuo\"}]";
    private BooleanModelField checkIn;
    private BooleanModelField receiveCash;

    public Boolean getDefaultEnable() {
        return true;
    }


    @Override
    public String getName() {
        return "天天赚现金";
    }

    @Override
    protected void addFields(ModelFields modelFields) {
        BooleanModelField booleanModelField = new BooleanModelField("checkIn", "每日签到",true);
        this.checkIn = booleanModelField;
        modelFields.addField(booleanModelField);
        BooleanModelField booleanModelField2 = new BooleanModelField("receiveCash", "累计满5元领取(每日最多一次)",true);
        this.receiveCash = booleanModelField2;
        modelFields.addField(booleanModelField2);
    }

    private JSONObject args() throws Exception {
        return new JSONObject().put("activityId", "SIGN_TASK_CENTER").put("source", "sousuo");
    }

    @Override
    protected void execute(IsolatedRewardTask.Run run) throws Exception {
        JSONObject jSONObjectOptJSONObject = run.query("alipay.membertangram.biz.rpc.newtaskcenter.signStatusQuery", QUERY).optJSONObject("signInfo");
        JSONObject jSONObject = null;
        JSONArray jSONArrayOptJSONArray = jSONObjectOptJSONObject == null ? null : jSONObjectOptJSONObject.optJSONArray("signDayInfos");
        if (jSONArrayOptJSONArray == null || jSONArrayOptJSONArray.length() > 62) {
            Log.record(getName() + "：缺少有效签到日历，停止本轮");
            return;
        }
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"));
        calendar.set(11, 0);
        calendar.set(12, 0);
        calendar.set(13, 0);
        calendar.set(14, 0);
        for (int i = 0; i < jSONArrayOptJSONArray.length(); i++) {
            JSONObject jSONObjectOptJSONObject2 = jSONArrayOptJSONArray.optJSONObject(i);
            if (jSONObjectOptJSONObject2 != null && jSONObjectOptJSONObject2.optLong("signDate", -1L) == calendar.getTimeInMillis()) {
                if (jSONObject != null) {
                    Log.record(getName() + "：签到日历重复，停止本轮");
                    return;
                }
                jSONObject = jSONObjectOptJSONObject2;
            }
        }
        if (jSONObject == null || !(jSONObject.opt("todaySignFlag") instanceof Boolean)) {
            Log.record(getName() + "：今日签到状态不明确，停止本轮");
            return;
        }
        if (!jSONObject.getBoolean("todaySignFlag") && this.checkIn.getValue().booleanValue()) {
            String strOptString = jSONObject.optString("sceneCode");
            if (strOptString.isEmpty() || strOptString.length() > 128) {
                Log.record(getName() + "：缺少签到场景，停止本轮");
                return;
            }
            if (run.onceToday("doSign", "alipay.membertangram.biz.rpc.newtaskcenter.doSign", new JSONArray().put(args().put("signSceneCode", strOptString)).toString(), new IsolatedRewardTask.Allowed() {
                @Override
                public final boolean isAllowed() {
                    return DailyCash.this.checkIn.getValue().booleanValue();
                }
            }) != null) {
                Log.record(getName() + "：签到接口返回成功");
            }
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(getName());
            sb.append(jSONObject.getBoolean("todaySignFlag") ? "：今日已签到" : "：签到开关未开启");
            Log.record(sb.toString());
        }
        String strOptString2 = run.query("alipay.membertangram.biz.rpc.newtaskcenter.benefitInfosQuery", QUERY).optString("signTotalAmount", "");
        if (!strOptString2.matches("[0-9]{1,9}(\\.[0-9]{1,2})?")) {
            Log.record(getName() + "：金额格式不明确，停止本轮");
            return;
        }
        BigDecimal bigDecimal = new BigDecimal(strOptString2);
        if (bigDecimal.compareTo(new BigDecimal("5")) < 0) {
            Log.record(getName() + "：尚未达到领取门槛");
            return;
        }
        if (!this.receiveCash.getValue().booleanValue()) {
            Log.record(getName() + "：达到门槛，领取开关未开启");
            return;
        }
        if (run.onceToday("receiveCash", "alipay.membertangram.biz.rpc.newtaskcenter.receiveCash", new JSONArray().put(args().put("cashAmount", "5.0").put("totalAmount", bigDecimal.toPlainString())).toString(), new IsolatedRewardTask.Allowed() {
            @Override
            public final boolean isAllowed() {
                return DailyCash.this.receiveCash.getValue().booleanValue();
            }
        }) != null) {
            Log.record(getName() + "：领取接口返回成功，请在官方页面核对到账");
        }
    }
}
