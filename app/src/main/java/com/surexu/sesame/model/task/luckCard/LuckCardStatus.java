package com.surexu.sesame.model.task.luckCard;

import com.surexu.sesame.data.ModelFields;
import com.surexu.sesame.data.modelFieldExt.BooleanModelField;
import com.surexu.sesame.model.task.rewardSupport.IsolatedRewardTask;
import com.surexu.sesame.util.Log;
import okhttp3.HttpUrl;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LuckCardStatus extends IsolatedRewardTask {
    private BooleanModelField inspect;

    public Boolean getDefaultEnable() {
        return true;
    }


    @Override
    public String getName() {
        return "好运卡任务状态";
    }

    @Override
    protected void addFields(ModelFields modelFields) {
        BooleanModelField booleanModelField = new BooleanModelField("inspect", "查询任务状态",true);
        this.inspect = booleanModelField;
        modelFields.addField(booleanModelField);
    }

    @Override
    protected void execute(IsolatedRewardTask.Run run) throws Exception {
        if (!this.inspect.getValue().booleanValue()) {
            Log.record(getName() + "：查询开关未开启");
            return;
        }
        JSONObject jSONObjectOptJSONObject = run.query("com.alipay.pcreditcardweb.activity.LuckCard.consult", HttpUrl.PATH_SEGMENT_ENCODE_SET_URI).optJSONObject("result");
        JSONObject jSONObjectOptJSONObject2 = jSONObjectOptJSONObject == null ? null : jSONObjectOptJSONObject.optJSONObject("taskInfo");
        if ((jSONObjectOptJSONObject2 != null ? jSONObjectOptJSONObject2.optString("taskCenterId", "") : "").isEmpty()) {
            Log.record(getName() + "：当前没有任务中心");
            return;
        }
        JSONArray jSONArrayOptJSONArray = run.query("com.alipay.pcreditcardweb.activity.LuckCard.queryTaskList", HttpUrl.PATH_SEGMENT_ENCODE_SET_URI).optJSONArray("result");
        if (jSONArrayOptJSONArray == null || jSONArrayOptJSONArray.length() > 100) {
            Log.record(getName() + "：任务列表格式不明确");
            return;
        }
        int i = 0;
        int i2 = 0;
        int i3 = 0;
        int i4 = 0;
        for (int i5 = 0; i5 < jSONArrayOptJSONArray.length(); i5++) {
            JSONObject jSONObjectOptJSONObject3 = jSONArrayOptJSONArray.optJSONObject(i5);
            if (jSONObjectOptJSONObject3 == null) {
                i4++;
            } else {
                i++;
                String strOptString = jSONObjectOptJSONObject3.optString("taskProcessStatus");
                if ("RECEIVE_SUCCESS".equals(strOptString)) {
                    i2++;
                } else if ("NONE_SIGNUP".equals(strOptString) || "SIGNUP_COMPLETE".equals(strOptString)) {
                    i3++;
                } else {
                    i4++;
                }
            }
        }
        Log.record(getName() + "：任务=" + i + "，已完成=" + i2 + "，待处理=" + i3 + "，其他=" + i4);
    }
}
