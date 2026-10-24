package com.surexu.sesame.model.task.youthPrivilege;

import com.surexu.sesame.data.ModelFields;
import com.surexu.sesame.data.modelFieldExt.BooleanModelField;
import com.surexu.sesame.model.task.rewardSupport.IsolatedRewardTask;
import com.surexu.sesame.model.task.rewardSupport.RewardRunPolicy;
import com.surexu.sesame.util.Log;
import org.json.JSONObject;

public final class YouthPrivilege extends IsolatedRewardTask {
    private BooleanModelField checkIn;

    public Boolean getDefaultEnable() {
        return true;
    }


    @Override
    public String getName() {
        return "青春特权签到";
    }

    @Override
    protected void addFields(ModelFields modelFields) {
        BooleanModelField booleanModelField = new BooleanModelField("checkIn", "领取签到奖励",true);
        this.checkIn = booleanModelField;
        modelFields.addField(booleanModelField);
    }

    @Override
    protected void execute(IsolatedRewardTask.Run run) throws Exception {
        JSONObject jSONObjectOptJSONObject = run.query("alipay.membertangram.biz.rpc.student.queryCheckInModel", "[{\"chInfo\":\"ch_appcenter__chsub_9patch\",\"skipTaskModule\":false}]").optJSONObject("studentCheckInInfo");
        if (jSONObjectOptJSONObject == null) {
            Log.record(getName() + "：未返回签到状态，本轮结束");
            return;
        }
        if (!RewardRunPolicy.mayCheckIn(jSONObjectOptJSONObject.optString("action"))) {
            Log.record(getName() + "：当前没有可执行的签到动作");
            return;
        }
        if (!this.checkIn.getValue().booleanValue()) {
            Log.record(getName() + "：可签到，领取开关未开启");
            return;
        }
        if (run.onceToday("checkIn", "alipay.membertangram.biz.rpc.student.checkIn", "[{\"source\":\"ch_appcenter__chsub_9patch\"}]", new IsolatedRewardTask.Allowed() {
            @Override
            public final boolean isAllowed() {
                return YouthPrivilege.this.checkIn.getValue().booleanValue();
            }
        }) != null) {
            Log.record(getName() + "：签到接口返回成功");
        }
    }
}
