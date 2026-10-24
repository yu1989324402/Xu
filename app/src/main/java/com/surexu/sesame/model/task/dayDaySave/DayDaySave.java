package com.surexu.sesame.model.task.dayDaySave;

import com.surexu.sesame.data.ModelFields;
import com.surexu.sesame.data.modelFieldExt.BooleanModelField;
import com.surexu.sesame.model.task.rewardSupport.IsolatedRewardTask;
import com.surexu.sesame.util.Log;
import org.json.JSONObject;

public final class DayDaySave extends IsolatedRewardTask {
    private BooleanModelField checkIn;

    public Boolean getDefaultEnable() {
        return true;
    }


    @Override
    public String getName() {
        return "理财稳当当签到";
    }

    @Override
    protected void addFields(ModelFields modelFields) {
        BooleanModelField booleanModelField = new BooleanModelField("checkIn", "每日签到",true);
        this.checkIn = booleanModelField;
        modelFields.addField(booleanModelField);
    }

    @Override
    protected void execute(IsolatedRewardTask.Run run) throws Exception {
        JSONObject jSONObjectOptJSONObject = run.query("com.alipay.ficcscenepromobff.needle.daydaysave.index", "[{\"bizScenario\":\"huangjinpiao\"}]").optJSONObject("result");
        if (jSONObjectOptJSONObject == null || !(jSONObjectOptJSONObject.opt("hasSignIn") instanceof Boolean)) {
            Log.record(getName() + "：缺少明确签到状态，停止本轮");
            return;
        }
        if (jSONObjectOptJSONObject.getBoolean("hasSignIn")) {
            Log.record(getName() + "：已经签到");
            return;
        }
        if (!this.checkIn.getValue().booleanValue()) {
            Log.record(getName() + "：未签到，签到开关未开启");
            return;
        }
        if (run.onceToday("signIn", "com.alipay.ficcscenepromobff.needle.daydaysave.signIn", "[null]", new IsolatedRewardTask.Allowed() {
            @Override
            public final boolean isAllowed() {
                return DayDaySave.this.checkIn.getValue().booleanValue();
            }
        }) != null) {
            Log.record(getName() + "：签到接口返回成功");
        }
    }
}
