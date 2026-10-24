package com.surexu.sesame.model.task.promoprodRewards;

import com.surexu.sesame.data.ModelFields;
import com.surexu.sesame.data.modelFieldExt.BooleanModelField;
import com.surexu.sesame.model.task.rewardSupport.IsolatedRewardTask;
import com.surexu.sesame.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

public final class PromoprodRewards extends IsolatedRewardTask {
    private BooleanModelField claimCompleted;
    private BooleanModelField inspect;

    public Boolean getDefaultEnable() {
        return true;
    }


    @Override
    public String getName() {
        return "实体红包任务";
    }

    @Override
    protected void addFields(ModelFields modelFields) {
        BooleanModelField booleanModelField = new BooleanModelField("inspect", "查询任务状态",true);
        this.inspect = booleanModelField;
        modelFields.addField(booleanModelField);
        BooleanModelField booleanModelField2 = new BooleanModelField("claimCompleted", "领取服务端已完成奖励",true);
        this.claimCompleted = booleanModelField2;
        modelFields.addField(booleanModelField2);
    }

    @Override
    protected void execute(IsolatedRewardTask.Run run) throws Exception {
        if (!this.inspect.getValue().booleanValue() && !this.claimCompleted.getValue().booleanValue()) {
            Log.record(getName() + "：查询和领取开关均未开启");
            return;
        }
        JSONArray jSONArrayOptJSONArray = run.query("alipay.promoprod.task.listQuery", "[{\"consultAccessFlag\":true,\"taskCenInfo\":\"MZVPQ0DScvD6NjaPJzk8iNRgSSvWpCuA\"}]").optJSONArray("taskDetailList");
        if (jSONArrayOptJSONArray == null || jSONArrayOptJSONArray.length() > 100) {
            Log.record(getName() + "：任务列表格式不明确");
            return;
        }
        int i = 0;
        int i2 = 0;
        int i3 = 0;
        for (int i4 = 0; i4 < jSONArrayOptJSONArray.length(); i4++) {
            JSONObject jSONObjectOptJSONObject = jSONArrayOptJSONArray.optJSONObject(i4);
            if (jSONObjectOptJSONObject == null) {
                i3++;
            } else {
                String strOptString = jSONObjectOptJSONObject.optString("taskType");
                String strOptString2 = jSONObjectOptJSONObject.optString("taskProcessStatus");
                if ("TRANSFORMER".equals(strOptString)) {
                    i++;
                    if ("RECEIVE_SUCCESS".equals(strOptString2)) {
                        i2++;
                    }
                } else {
                    i3++;
                }
            }
        }
        Log.record(getName() + "：实体红包任务=" + i + "，已完成状态=" + i2 + "，其他任务=" + i3 + "，领取流程待服务端参数确认");
        if (!this.claimCompleted.getValue().booleanValue() || i2 <= 0) {
            return;
        }
        Log.record(getName() + "：发现已完成任务，但奖励参数未确认，本轮不发送领取请求");
    }
}
